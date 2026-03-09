package com.finance.userservice.service.impl;

import com.finance.userservice.dto.group.GroupAccountDto;
import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupAccount;
import com.finance.userservice.entity.User;
import com.finance.userservice.exception.AuthenticationException;
import com.finance.userservice.exception.ResourceNotFoundException;
import com.finance.userservice.repository.GroupAccountRepository;
import com.finance.userservice.repository.GroupMemberRepository;
import com.finance.userservice.repository.GroupRepository;
import com.finance.userservice.repository.UserRepository;
import com.finance.userservice.constant.GroupActivityType;
import com.finance.userservice.service.GroupAccountService;
import com.finance.userservice.service.GroupActivityService;
import com.finance.userservice.service.client.FinanceAccountClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import feign.FeignException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.Map;

import static com.finance.userservice.common.utils.SecurityUtils.getCurrentUsername;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupAccountServiceImpl implements GroupAccountService {

    private final GroupRepository groupRepository;
    private final GroupAccountRepository groupAccountRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final FinanceAccountClient financeAccountClient;
    private final GroupActivityService groupActivityService;
    private final com.finance.userservice.service.KafkaProducerService kafkaProducerService;
    private final com.finance.userservice.service.crypto.PiiCryptoService piiCryptoService;

    @Override
    @Transactional(readOnly = true)
    public Page<GroupAccountDto> list(Long groupId, String search, Pageable pageable) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        ensureMember(group, currentUserId);
        
        Page<GroupAccount> groupAccounts = groupAccountRepository.searchByGroup(group, ObjectUtils.isEmpty(search) ? null : search, pageable);
        
        // Fetch real-time account data from FinanceService
        java.util.List<Long> accountIds = groupAccounts.getContent().stream()
                .map(GroupAccount::getAccountId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        
        // Create a map of accountId -> real-time account data
        java.util.Map<Long, FinanceAccountClient.AccountResponse> accountDataMap = new java.util.HashMap<>();
        if (!accountIds.isEmpty()) {
            try {
                java.util.List<FinanceAccountClient.AccountResponse> accounts = financeAccountClient.getAccountsByIds(accountIds);
                for (FinanceAccountClient.AccountResponse acc : accounts) {
                    if (acc != null && acc.getId() != null) {
                        accountDataMap.put(acc.getId(), acc);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch real-time account data, using cached data: {}", e.getMessage());
            }
        }
        
        // Map to DTOs using real-time data
        final java.util.Map<Long, FinanceAccountClient.AccountResponse> finalAccountDataMap = accountDataMap;
        return groupAccounts.map(ga -> toDtoWithRealTimeData(ga, finalAccountDataMap.get(ga.getAccountId())));
    }

    @Override
    @Transactional
    public GroupAccountDto link(Long groupId, Long accountId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        ensureMember(group, currentUserId);

        groupAccountRepository.findByGroupAndAccountId(group, accountId).ifPresent(existing -> {
            throw new IllegalArgumentException("Account already linked to group");
        });

        // Fetch account data from FinanceService to verify ownership and get bankAccountId
        FinanceAccountClient.AccountResponse accountResponse;
        try {
            accountResponse = financeAccountClient.getAccountById(accountId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Account", "Id", String.valueOf(accountId));
        }
        if (!String.valueOf(currentUserId).equals(accountResponse.getUserId())) {
            throw new AuthenticationException("You can only link your own accounts");
        }

        // Only store essential reference fields - other data will be fetched real-time
        GroupAccount groupAccount = new GroupAccount();
        groupAccount.setGroup(group);
        groupAccount.setAccountId(accountId);
        groupAccount.setBankAccountId(accountResponse.getBankAccountId()); // Keep for transaction queries
        groupAccount.setOwnerUserId(currentUserId);
        // No longer storing: label, accountNumber, bankBrandName, bankCode, currency, accumulated
        // These will be fetched in real-time when listing

        GroupAccount saved = groupAccountRepository.save(groupAccount);
        
        // Build DTO with real-time data for response
        GroupAccountDto dto = toDtoWithRealTimeData(saved, accountResponse);

        // log activity: account linked
        String bankName = ObjectUtils.isEmpty(accountResponse.getBankFullName()) 
                ? accountResponse.getBankShortName() 
                : accountResponse.getBankFullName();
        groupActivityService.logActivity(
                group,
                currentUserId,
                null,
                GroupActivityType.ACCOUNT_LINKED,
                "Account linked to group",
                Map.of(
                        "accountId", accountId,
                        "accountNumber", accountResponse.getAccountNumber() != null ? accountResponse.getAccountNumber() : "",
                        "bankName", bankName != null ? bankName : ""
                )
        );

        // Send notification to all group members
        sendGroupAccountLinkedNotification(group, currentUserId, accountResponse);

        return dto;
    }

    @Override
    @Transactional
    public void unlink(Long groupId, Long accountId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        ensureMember(group, currentUserId);

        GroupAccount groupAccount = groupAccountRepository.findByGroupAndAccountId(group, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupAccount", "accountId", String.valueOf(accountId)));

        if (!groupAccount.getOwnerUserId().equals(currentUserId)) {
            throw new AuthenticationException("You can only remove accounts you linked");
        }

        // Fetch account data for logging before deletion
        String accountNumber = "";
        String bankName = "";
        try {
            FinanceAccountClient.AccountResponse accountResponse = financeAccountClient.getAccountById(accountId);
            accountNumber = accountResponse.getAccountNumber() != null ? accountResponse.getAccountNumber() : "";
            bankName = ObjectUtils.isEmpty(accountResponse.getBankFullName()) 
                    ? (accountResponse.getBankShortName() != null ? accountResponse.getBankShortName() : "")
                    : accountResponse.getBankFullName();
        } catch (Exception e) {
            log.warn("Failed to fetch account data for logging: {}", e.getMessage());
        }

        groupAccountRepository.delete(groupAccount);

        // log activity: account unlinked
        groupActivityService.logActivity(
                group,
                currentUserId,
                null,
                GroupActivityType.ACCOUNT_UNLINKED,
                "Account unlinked from group",
                Map.of(
                        "accountId", accountId,
                        "accountNumber", accountNumber,
                        "bankName", bankName
                )
        );

        // Send notification to all group members
        sendGroupAccountUnlinkedNotification(group, currentUserId, accountNumber, bankName);
    }

    /**
     * Convert GroupAccount to DTO with only reference data (no account details).
     * This is used as fallback when real-time data is not available.
     */
    private GroupAccountDto toDto(GroupAccount groupAccount) {
        return GroupAccountDto.builder()
                .id(groupAccount.getId())
                .accountId(groupAccount.getAccountId())
                .bankAccountId(groupAccount.getBankAccountId())
                .ownerUserId(groupAccount.getOwnerUserId())
                .linkedAt(groupAccount.getLinkedAt())
                // Other fields will be null - should fetch real-time data
                .build();
    }

    /**
     * Convert GroupAccount to DTO using real-time account data from FinanceService.
     * Falls back to basic DTO if real-time data is not available.
     */
    private GroupAccountDto toDtoWithRealTimeData(GroupAccount groupAccount, FinanceAccountClient.AccountResponse realTimeData) {
        if (realTimeData != null) {
            // Use real-time data for all account details
            return GroupAccountDto.builder()
                    .id(groupAccount.getId())
                    .accountId(groupAccount.getAccountId())
                    .bankAccountId(realTimeData.getBankAccountId() != null ? realTimeData.getBankAccountId() : groupAccount.getBankAccountId())
                    .ownerUserId(groupAccount.getOwnerUserId())
                    .label(realTimeData.getLabel())
                    .bankBrandName(getBankBrandName(realTimeData))
                    .accountNumber(realTimeData.getAccountNumber())
                    .accumulated(realTimeData.getAccumulated())
                    .currency(realTimeData.getCurrency())
                    .bankCode(realTimeData.getBankCode())
                    .linkedAt(groupAccount.getLinkedAt())
                    .build();
        }
        // Fallback to basic DTO if real-time data is not available
        return toDto(groupAccount);
    }

    private String getBankBrandName(FinanceAccountClient.AccountResponse realTimeData) {
        if (!ObjectUtils.isEmpty(realTimeData.getBankFullName())) {
            return realTimeData.getBankFullName();
        }
        if (!ObjectUtils.isEmpty(realTimeData.getBankShortName())) {
            return realTimeData.getBankShortName();
        }
        return null;
    }

    private void ensureMember(Group group, Long userId) {
        if (group.getOwnerUserId().equals(userId)) {
            return;
        }
        groupMemberRepository.findByGroupAndUserId(group, userId)
                .orElseThrow(() -> new AuthenticationException("You are not a member of this group"));
    }

    private Long resolveCurrentUserId() {
        String username = getCurrentUsername();
        if (ObjectUtils.isEmpty(username)) {
            throw new AuthenticationException("No authenticated username found");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Authenticated user not found"));
        return user.getId();
    }

    private void sendGroupAccountLinkedNotification(Group group, Long linkerUserId, FinanceAccountClient.AccountResponse accountResponse) {
        try {
            User linker = userRepository.findById(linkerUserId).orElse(null);
            String linkerFullName = null;
            if (linker != null && linker.getFullName() != null) {
                try {
                    linkerFullName = piiCryptoService.decrypt(linker.getId(), linker.getFullName());
                } catch (Exception e) {
                    log.warn("Failed to decrypt linker full name: {}", e.getMessage());
                }
            }
            String linkerName = !ObjectUtils.isEmpty(linkerFullName)
                    ? linkerFullName
                    : (linker != null ? linker.getUsername() : "Thành viên");

            String bankName = getBankBrandName(accountResponse);
            String accountNumber = accountResponse.getAccountNumber() != null ? accountResponse.getAccountNumber() : "";

            java.util.List<com.finance.userservice.entity.GroupMember> members = groupMemberRepository.findByGroup(group);
            for (com.finance.userservice.entity.GroupMember member : members) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = null;
                    String email = null;
                    String phone = null;
                    if (user.getFullName() != null) {
                        fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    }
                    if (user.getEmail() != null) {
                        email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    }
                    if (user.getPhone() != null) {
                        phone = piiCryptoService.decrypt(user.getId(), user.getPhone());
                    }

                    java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("linkerUserId", linkerUserId);
                    metadata.put("linkerName", linkerName);
                    metadata.put("accountId", accountResponse.getId());
                    metadata.put("accountNumber", accountNumber);
                    metadata.put("bankName", bankName != null ? bankName : "");

                    com.finance.userservice.event.UserNotificationEvent event = com.finance.userservice.event.UserNotificationEvent.builder()
                            .eventId(java.util.UUID.randomUUID().toString())
                            .eventType(com.finance.userservice.event.UserNotificationEvent.GROUP_ACCOUNT_LINKED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Tài khoản đã được liên kết")
                            .message(String.format("%s đã liên kết tài khoản %s (%s) vào nhóm \"%s\"",
                                    linkerName, accountNumber, bankName != null ? bankName : "Ngân hàng", group.getName()))
                            .timestamp(java.time.LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send account linked notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send group account linked notifications: {}", ex.getMessage(), ex);
        }
    }

    private void sendGroupAccountUnlinkedNotification(Group group, Long unlinkerUserId, String accountNumber, String bankName) {
        try {
            User unlinker = userRepository.findById(unlinkerUserId).orElse(null);
            String unlinkerFullName = null;
            if (unlinker != null && unlinker.getFullName() != null) {
                try {
                    unlinkerFullName = piiCryptoService.decrypt(unlinker.getId(), unlinker.getFullName());
                } catch (Exception e) {
                    log.warn("Failed to decrypt unlinker full name: {}", e.getMessage());
                }
            }
            String unlinkerName = !ObjectUtils.isEmpty(unlinkerFullName)
                    ? unlinkerFullName
                    : (unlinker != null ? unlinker.getUsername() : "Thành viên");

            java.util.List<com.finance.userservice.entity.GroupMember> members = groupMemberRepository.findByGroup(group);
            for (com.finance.userservice.entity.GroupMember member : members) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = null;
                    String email = null;
                    String phone = null;
                    if (user.getFullName() != null) {
                        fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    }
                    if (user.getEmail() != null) {
                        email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    }
                    if (user.getPhone() != null) {
                        phone = piiCryptoService.decrypt(user.getId(), user.getPhone());
                    }

                    java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("unlinkerUserId", unlinkerUserId);
                    metadata.put("unlinkerName", unlinkerName);
                    metadata.put("accountNumber", accountNumber);
                    metadata.put("bankName", bankName);

                    com.finance.userservice.event.UserNotificationEvent event = com.finance.userservice.event.UserNotificationEvent.builder()
                            .eventId(java.util.UUID.randomUUID().toString())
                            .eventType(com.finance.userservice.event.UserNotificationEvent.GROUP_ACCOUNT_UNLINKED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Tài khoản đã bị gỡ liên kết")
                            .message(String.format("%s đã gỡ liên kết tài khoản %s (%s) khỏi nhóm \"%s\"",
                                    unlinkerName, accountNumber, bankName != null ? bankName : "Ngân hàng", group.getName()))
                            .timestamp(java.time.LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send account unlinked notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send group account unlinked notifications: {}", ex.getMessage(), ex);
        }
    }
}


