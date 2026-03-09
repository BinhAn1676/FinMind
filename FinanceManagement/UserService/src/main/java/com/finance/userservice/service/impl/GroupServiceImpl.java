package com.finance.userservice.service.impl;

import com.finance.userservice.constant.GroupActivityType;
import com.finance.userservice.constant.GroupInviteStatus;
import com.finance.userservice.constant.GroupRole;
import com.finance.userservice.dto.file.FileDto;
import com.finance.userservice.dto.group.GroupCreateRequest;
import com.finance.userservice.dto.group.GroupDto;
import com.finance.userservice.dto.group.GroupInviteDto;
import com.finance.userservice.dto.group.GroupUpdateRequest;
import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupAccount;
import com.finance.userservice.entity.GroupInvitation;
import com.finance.userservice.entity.GroupMember;
import com.finance.userservice.entity.User;
import com.finance.userservice.event.UserNotificationEvent;
import com.finance.userservice.exception.AuthenticationException;
import com.finance.userservice.exception.ResourceNotFoundException;
import com.finance.userservice.repository.GroupAccountRepository;
import com.finance.userservice.repository.GroupActivityRepository;
import com.finance.userservice.repository.GroupInvitationRepository;
import com.finance.userservice.repository.GroupMemberRepository;
import com.finance.userservice.repository.GroupRepository;
import com.finance.userservice.repository.UserRepository;
import com.finance.userservice.service.GroupActivityService;
import com.finance.userservice.service.GroupService;
import com.finance.userservice.service.KafkaProducerService;
import com.finance.userservice.service.crypto.PiiCryptoService;
import com.finance.userservice.service.client.FileStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.finance.userservice.common.utils.SecurityUtils.getCurrentUsername;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAccountRepository groupAccountRepository;
    private final GroupActivityRepository groupActivityRepository;
    private final FileStorageClient fileStorageClient;
    private final GroupInvitationRepository groupInvitationRepository;
    private final UserRepository userRepository;
    private final KafkaProducerService kafkaProducerService;
    private final PiiCryptoService piiCryptoService;
    private final GroupActivityService groupActivityService;

    @Override
    @Transactional
    public GroupDto create(GroupCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        if (request == null || ObjectUtils.isEmpty(request.getName())) {
            throw new IllegalArgumentException("Group name is required");
        }
        Group g = new Group();
        g.setName(request.getName());
        g.setDescription(request.getDescription());
        if (!ObjectUtils.isEmpty(request.getAvatarFileId())) {
            g.setAvatarFileId(request.getAvatarFileId());
        }
        g.setOwnerUserId(currentUserId);
        g = groupRepository.saveAndFlush(g);

        GroupMember owner = new GroupMember();
        owner.setGroup(g);
        owner.setUserId(currentUserId);
        owner.setRole(GroupRole.ADMIN);
        groupMemberRepository.save(owner);

        if (!ObjectUtils.isEmpty(request.getInvitedUserIds())) {
            log.info("Group {} received {} invite targets", g.getId(), request.getInvitedUserIds().size());
        }

        // log activity: group created
        groupActivityService.logActivity(
                g,
                currentUserId,
                null,
                GroupActivityType.GROUP_CREATED,
                "Group created",
                Map.of()
        );

        return toDto(g, true);
    }

    @Override
    public Page<GroupDto> search(String query, Pageable pageable) {
        Long currentUserId = resolveCurrentUserId();
        return groupRepository.searchByMember(query, currentUserId, pageable).map(g -> toDto(g, false));
    }

    @Override
    public GroupDto getById(Long id) {
        Group g = groupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(id)));
        return toDto(g, true);
    }

    @Override
    @Transactional
    public GroupDto update(Long id, GroupUpdateRequest request) {
        Group g = groupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(id)));
        Long currentUserId = resolveCurrentUserId();
        ensureCanUpdate(g, currentUserId);

        if (!ObjectUtils.isEmpty(request.getName())) {
            g.setName(request.getName());
        }
        if (request.getDescription() != null) {
            g.setDescription(request.getDescription());
        }
        if (!ObjectUtils.isEmpty(request.getAvatarFileId())) {
            g.setAvatarFileId(request.getAvatarFileId());
        }
        g = groupRepository.save(g);

        // Send notification to all members about group update
        sendGroupUpdateNotification(g, currentUserId);

        return toDto(g, true);
    }

    @Override
    @Transactional
    public GroupDto updateAvatar(Long id, MultipartFile avatarFile) {
        Group g = groupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(id)));
        if (avatarFile == null || avatarFile.isEmpty()) {
            return toDto(g, true);
        }
        try {
            FileDto uploaded = fileStorageClient.upload(String.valueOf(g.getId()), "GROUP_AVATAR", avatarFile, "Group avatar");
            g.setAvatarFileId(uploaded.getId());
            groupRepository.save(g);
        } catch (Exception e) {
            log.warn("Failed to upload group avatar: {}", e.getMessage());
        }
        return toDto(g, true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(id)));
        Long currentUserId = resolveCurrentUserId();
        ensureCanUpdate(group, currentUserId);

        // Send notification to all members before deleting
        sendGroupDeletedNotification(group, currentUserId);

        groupInvitationRepository.deleteByGroup(group);
        groupAccountRepository.deleteByGroup(group);
        groupMemberRepository.deleteByGroup(group);
        groupActivityRepository.deleteByGroup(group);
        groupRepository.delete(group);
        log.info("Group {} deleted by user {}", id, currentUserId);
    }

    @Override
    @Transactional
    public List<GroupInviteDto> inviteMembers(Long groupId, List<Long> inviteeIds) {
        if (inviteeIds == null || inviteeIds.isEmpty()) {
            return List.of();
        }
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        ensureCanInvite(group, currentUserId);

        List<GroupInviteDto> result = new ArrayList<>();
        // Resolve inviter name once
        String inviterName = resolveUserFullName(currentUserId);
        for (Long inviteeId : inviteeIds) {
            if (inviteeId == null || inviteeId.equals(currentUserId)) {
                continue;
            }
            if (groupMemberRepository.findByGroupAndUserId(group, inviteeId).isPresent()) {
                continue;
            }
            GroupInvitation invitation = groupInvitationRepository.findByGroupAndInviteeUserId(group, inviteeId)
                    .map(existing -> {
                        if (existing.getStatus() != GroupInviteStatus.PENDING) {
                            existing.setStatus(GroupInviteStatus.PENDING);
                            existing.setInviterUserId(currentUserId);
                        }
                        return existing;
                    })
                    .orElseGet(() -> {
                        GroupInvitation created = new GroupInvitation();
                        created.setGroup(group);
                        created.setInviterUserId(currentUserId);
                        created.setInviteeUserId(inviteeId);
                        created.setStatus(GroupInviteStatus.PENDING);
                        return created;
                    });
            GroupInvitation saved = groupInvitationRepository.save(invitation);
            result.add(toInviteDto(saved));
            sendGroupInvitationNotification(group, saved, currentUserId);

            // log activity: invite sent
            String inviteeName = resolveUserFullName(inviteeId);
            groupActivityService.logActivity(
                    group,
                    currentUserId,
                    inviterName,
                    GroupActivityType.INVITE_SENT,
                    "Invite sent",
                    Map.of(
                            "inviteeUserId", inviteeId,
                            "inviteeName", inviteeName
                    )
            );
        }
        return result;
    }

    @Override
    @Transactional
    public GroupInviteDto respondToInvite(Long groupId, Long inviteId, boolean accept) {
        GroupInvitation invitation = groupInvitationRepository.findByIdAndGroupId(inviteId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group invitation", "Id", inviteId.toString()));
        Long currentUserId = resolveCurrentUserId();
        if (!currentUserId.equals(invitation.getInviteeUserId())) {
            throw new AuthenticationException("Only invitee can respond to the invitation");
        }
        if (invitation.getStatus() != GroupInviteStatus.PENDING) {
            return toInviteDto(invitation);
        }
        if (accept) {
            invitation.setStatus(GroupInviteStatus.ACCEPTED);
            groupInvitationRepository.save(invitation);
            addMemberIfAbsent(invitation.getGroup(), currentUserId);

            groupActivityService.logActivity(
                    invitation.getGroup(),
                    currentUserId,
                    resolveUserFullName(currentUserId),
                    GroupActivityType.INVITE_ACCEPTED,
                    "Invite accepted",
                    Map.of(
                            "inviteId", inviteId,
                            "inviterUserId", invitation.getInviterUserId()
                    )
            );
        } else {
            invitation.setStatus(GroupInviteStatus.REJECTED);
            groupInvitationRepository.save(invitation);

            groupActivityService.logActivity(
                    invitation.getGroup(),
                    currentUserId,
                    resolveUserFullName(currentUserId),
                    GroupActivityType.INVITE_REJECTED,
                    "Invite rejected",
                    Map.of(
                            "inviteId", inviteId,
                            "inviterUserId", invitation.getInviterUserId()
                    )
            );
        }
        return toInviteDto(invitation);
    }

    @Override
    @Transactional
    public void leaveGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();

        GroupMember member = groupMemberRepository.findByGroupAndUserId(group, currentUserId)
                .orElseThrow(() -> new AuthenticationException("You are not a member of this group"));

        // If admin, ensure there is at least one other admin remaining
        if (member.getRole() == GroupRole.ADMIN) {
            long adminCount = groupMemberRepository.countByGroupAndRole(group, GroupRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException("Group must have at least one admin");
            }
        }

        groupMemberRepository.delete(member);

        GroupActivityType type = member.getRole() == GroupRole.ADMIN
                ? GroupActivityType.ADMIN_LEFT
                : GroupActivityType.MEMBER_LEFT;

        groupActivityService.logActivity(
                group,
                currentUserId,
                null,
                type,
                "Member left group",
                Map.of("userId", currentUserId)
        );

        // Send notification to all remaining members
        sendMemberLeftNotification(group, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupInviteDto> getInvites(Long groupId, GroupInviteStatus status, Pageable pageable) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        // Allow all members to view invites, not just admins/moderators
        ensureCanViewInvites(group, currentUserId);
        if (status == null) {
            return groupInvitationRepository.findByGroupAndStatus(group, GroupInviteStatus.PENDING, pageable)
                    .map(this::toInviteDto);
        }
        return groupInvitationRepository.findByGroupAndStatus(group, status, pageable)
                .map(this::toInviteDto);
    }

    private String resolveUserFullName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(u -> {
                    if (u.getFullName() == null) {
                        return null;
                    }
                    try {
                        return piiCryptoService.decrypt(u.getId(), u.getFullName());
                    } catch (Exception e) {
                        log.warn("Failed to decrypt fullName for user {}: {}", u.getId(), e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    private GroupDto toDto(Group g, boolean includeMembers) {
        Integer memberCount = null;
        var builder = GroupDto.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .avatarFileId(g.getAvatarFileId())
                .ownerUserId(g.getOwnerUserId())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt());
        if (includeMembers) {
            var memberEntities = groupMemberRepository.findByGroup(g);
            memberCount = memberEntities.size();
            var members = buildMemberDtos(memberEntities);
            builder.members(members);
        } else {
            memberCount = (int) groupMemberRepository.countByGroup(g);
        }
        builder.memberCount(memberCount);
        return builder.build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupDto.MemberDto> getMembers(Long groupId, Pageable pageable) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        // Ensure user is at least a member of the group
        groupMemberRepository.findByGroupAndUserId(group, currentUserId)
                .orElseThrow(() -> new AuthenticationException("You are not a member of this group"));
        Page<GroupMember> page = groupMemberRepository.findByGroup(group, pageable);
        return page.map(m -> buildMemberDtos(List.of(m)).get(0));
    }

    @Override
    @Transactional
    public GroupInviteDto cancelInvite(Long groupId, Long inviteId) {
        GroupInvitation invitation = groupInvitationRepository.findByIdAndGroupId(inviteId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group invitation", "Id", inviteId.toString()));
        Long currentUserId = resolveCurrentUserId();
        // Only inviter or group admins/owner can cancel
        Group group = invitation.getGroup();
        if (!currentUserId.equals(invitation.getInviterUserId())) {
            ensureCanInvite(group, currentUserId);
        }
        if (invitation.getStatus() != GroupInviteStatus.PENDING) {
            return toInviteDto(invitation);
        }
        invitation.setStatus(GroupInviteStatus.CANCELLED);
        groupInvitationRepository.save(invitation);
        return toInviteDto(invitation);
    }

    @Override
    @Transactional
    public GroupDto.MemberDto updateMemberRole(Long groupId, Long userId, com.finance.userservice.dto.group.UpdateMemberRoleRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        
        // Only group owner or admins can update member roles
        ensureCanManageMembers(group, currentUserId);
        
        GroupMember member = groupMemberRepository.findByGroupAndUserId(group, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Group member", "userId", String.valueOf(userId)));
        
        // Prevent changing owner's role
        if (group.getOwnerUserId().equals(userId)) {
            throw new IllegalStateException("Cannot change owner's role");
        }
        
        // If changing from ADMIN to non-ADMIN, ensure at least one admin remains
        if (member.getRole() == GroupRole.ADMIN && request.getRole() != GroupRole.ADMIN) {
            long adminCount = groupMemberRepository.countByGroupAndRole(group, GroupRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException("Group must have at least one admin");
            }
        }
        
        GroupRole oldRole = member.getRole();
        member.setRole(request.getRole());
        groupMemberRepository.save(member);
        
        // Log activity
        String memberName = resolveUserFullName(userId);
        groupActivityService.logActivity(
                group,
                currentUserId,
                resolveUserFullName(currentUserId),
                GroupActivityType.MEMBER_ROLE_CHANGED,
                "Member role changed",
                Map.of(
                        "targetUserId", userId,
                        "targetUserName", memberName != null ? memberName : "Unknown",
                        "oldRole", oldRole.name(),
                        "newRole", request.getRole().name()
                )
        );

        // Send notification to affected member and all other members
        sendMemberRoleChangedNotification(group, userId, oldRole, request.getRole(), currentUserId);

        return buildMemberDtos(List.of(member)).get(0);
    }

    @Override
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "Id", String.valueOf(groupId)));
        Long currentUserId = resolveCurrentUserId();
        
        // Only group owner or admins can remove members
        ensureCanManageMembers(group, currentUserId);
        
        // Cannot remove owner
        if (group.getOwnerUserId().equals(userId)) {
            throw new IllegalStateException("Cannot remove group owner");
        }
        
        GroupMember member = groupMemberRepository.findByGroupAndUserId(group, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Group member", "userId", String.valueOf(userId)));
        
        // If removing admin, ensure at least one admin remains
        if (member.getRole() == GroupRole.ADMIN) {
            long adminCount = groupMemberRepository.countByGroupAndRole(group, GroupRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException("Group must have at least one admin");
            }
        }
        
        // Delete all group accounts linked by this user in this group
        List<GroupAccount> userAccounts = groupAccountRepository.findByGroupAndOwnerUserId(group, userId);
        if (!userAccounts.isEmpty()) {
            groupAccountRepository.deleteAll(userAccounts);
            log.info("Deleted {} group accounts for user {} in group {}", userAccounts.size(), userId, groupId);
        }
        
        // Delete the member
        groupMemberRepository.delete(member);

        // Log activity
        String memberName = resolveUserFullName(userId);
        groupActivityService.logActivity(
                group,
                currentUserId,
                resolveUserFullName(currentUserId),
                GroupActivityType.MEMBER_REMOVED,
                "Member removed from group",
                Map.of(
                        "targetUserId", userId,
                        "targetUserName", memberName != null ? memberName : "Unknown",
                        "removedRole", member.getRole().name()
                )
        );

        // Send notification to removed member and all remaining members
        sendMemberRemovedNotification(group, userId, currentUserId);
    }

    private List<GroupDto.MemberDto> buildMemberDtos(List<GroupMember> memberEntities) {
        if (memberEntities == null || memberEntities.isEmpty()) {
            return List.of();
        }
        var userIds = memberEntities.stream()
                .map(GroupMember::getUserId)
                .toList();
        var users = userRepository.findAllById(userIds);
        var userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        return memberEntities.stream().map(m -> {
            User user = userMap.get(m.getUserId());
            String fullName = null;
            String email = null;
            String phone = null;
            String avatar = null;
            if (user != null) {
                avatar = user.getAvatar();
                if (user.getFullName() != null) {
                    try {
                        fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    } catch (Exception e) {
                        log.warn("Failed to decrypt fullName for user {}: {}", user.getId(), e.getMessage());
                    }
                }
                if (user.getEmail() != null) {
                    try {
                        email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    } catch (Exception e) {
                        log.warn("Failed to decrypt email for user {}: {}", user.getId(), e.getMessage());
                    }
                }
                if (user.getPhone() != null) {
                    try {
                        phone = piiCryptoService.decrypt(user.getId(), user.getPhone());
                    } catch (Exception e) {
                        log.warn("Failed to decrypt phone for user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
            return GroupDto.MemberDto.builder()
                    .userId(m.getUserId())
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .avatar(avatar)
                    .role(m.getRole().name())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).toList();
    }

    private GroupInviteDto toInviteDto(GroupInvitation invitation) {
        AtomicReference<String> fullNameRef = new AtomicReference<>();
        AtomicReference<String> emailRef = new AtomicReference<>();
        AtomicReference<String> phoneRef = new AtomicReference<>();
        try {
            userRepository.findById(invitation.getInviteeUserId()).ifPresent(user -> {
                try {
                    if (user.getFullName() != null) {
                        fullNameRef.set(piiCryptoService.decrypt(user.getId(), user.getFullName()));
                    }
                    if (user.getEmail() != null) {
                        emailRef.set(piiCryptoService.decrypt(user.getId(), user.getEmail()));
                    }
                    if (user.getPhone() != null) {
                        phoneRef.set(piiCryptoService.decrypt(user.getId(), user.getPhone()));
                    }
                } catch (Exception e) {
                    log.warn("Failed to decrypt invitee info for user {}: {}", user.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to load invitee user {}: {}", invitation.getInviteeUserId(), e.getMessage());
        }

        return GroupInviteDto.builder()
                .id(invitation.getId())
                .groupId(invitation.getGroup().getId())
                .inviterUserId(invitation.getInviterUserId())
                .inviteeUserId(invitation.getInviteeUserId())
                .inviteeFullName(fullNameRef.get())
                .inviteeEmail(emailRef.get())
                .inviteePhone(phoneRef.get())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }

    private void addMemberIfAbsent(Group group, Long userId) {
        Optional<GroupMember> member = groupMemberRepository.findByGroupAndUserId(group, userId);
        if (member.isEmpty()) {
            GroupMember newMember = new GroupMember();
            newMember.setGroup(group);
            newMember.setUserId(userId);
            newMember.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(newMember);
        }
    }

    private void ensureCanViewInvites(Group group, Long currentUserId) {
        // Owner can always view
        if (group.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        // Check if user is a member of the group
        Optional<GroupMember> member = groupMemberRepository.findByGroupAndUserId(group, currentUserId);
        if (member.isEmpty()) {
            throw new AuthenticationException("Not authorized to view invites");
        }
        // All members can view invites
    }

    private void ensureCanInvite(Group group, Long currentUserId) {
        if (group.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        Optional<GroupMember> member = groupMemberRepository.findByGroupAndUserId(group, currentUserId);
        if (member.isEmpty()) {
            throw new AuthenticationException("Not authorized to manage invites");
        }
        GroupRole role = member.get().getRole();
        if (role != GroupRole.ADMIN && role != GroupRole.MODERATOR) {
            throw new AuthenticationException("Not authorized to manage invites");
        }
    }

    private void ensureCanManageMembers(Group group, Long currentUserId) {
        if (group.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        Optional<GroupMember> member = groupMemberRepository.findByGroupAndUserId(group, currentUserId);
        if (member.isEmpty()) {
            throw new AuthenticationException("Not authorized to manage members");
        }
        GroupRole role = member.get().getRole();
        if (role != GroupRole.ADMIN) {
            throw new AuthenticationException("Only ADMIN can manage members");
        }
    }

    private void ensureCanUpdate(Group group, Long currentUserId) {
        if (group.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        Optional<GroupMember> member = groupMemberRepository.findByGroupAndUserId(group, currentUserId);
        if (member.isEmpty()) {
            throw new AuthenticationException("Not authorized to update group");
        }
        GroupRole role = member.get().getRole();
        if (role != GroupRole.ADMIN) {
            throw new AuthenticationException("Only ADMIN can update group");
        }
    }

    private void sendGroupInvitationNotification(Group group, GroupInvitation invitation, Long inviterUserId) {
        try {
            User invitee = userRepository.findById(invitation.getInviteeUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "Id", String.valueOf(invitation.getInviteeUserId())));
            User inviter = userRepository.findById(inviterUserId).orElse(null);

            String inviterFullName = inviter != null ? piiCryptoService.decrypt(inviter.getId(), inviter.getFullName()) : null;
            String inviterName = !ObjectUtils.isEmpty(inviterFullName)
                    ? inviterFullName
                    : (inviter != null ? inviter.getUsername() : "Hệ thống");

            String inviteeFullName = piiCryptoService.decrypt(invitee.getId(), invitee.getFullName());
            String inviteeEmail = piiCryptoService.decrypt(invitee.getId(), invitee.getEmail());
            String inviteePhone = piiCryptoService.decrypt(invitee.getId(), invitee.getPhone());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("groupId", group.getId());
            metadata.put("groupName", group.getName());
            metadata.put("invitationId", invitation.getId());
            metadata.put("inviterUserId", inviterUserId);
            metadata.put("inviterName", inviterName);

            UserNotificationEvent event = UserNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(UserNotificationEvent.GROUP_INVITATION)
                    .userId(invitee.getId())
                    .email(inviteeEmail)
                    .fullName(inviteeFullName)
                    .phone(inviteePhone)
                    .username(invitee.getUsername())
                    .source(inviter != null ? inviter.getUsername() : "group-service")
                    .title("Lời mời tham gia nhóm")
                    .message(String.format("%s đã mời bạn tham gia nhóm \"%s\"", inviterName, group.getName()))
                    .timestamp(LocalDateTime.now())
                    .additionalData(metadata)
                    .build();
            kafkaProducerService.sendNotificationEvent(event);
        } catch (ResourceNotFoundException ex) {
            log.warn("Unable to send notification, user missing: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to send group invitation notification event: {}", ex.getMessage(), ex);
        }
    }

    private void sendGroupUpdateNotification(Group group, Long updaterUserId) {
        try {
            User updater = userRepository.findById(updaterUserId).orElse(null);
            String updaterFullName = updater != null ? piiCryptoService.decrypt(updater.getId(), updater.getFullName()) : null;
            String updaterName = !ObjectUtils.isEmpty(updaterFullName)
                    ? updaterFullName
                    : (updater != null ? updater.getUsername() : "Quản trị viên");

            List<GroupMember> members = groupMemberRepository.findByGroup(group);
            for (GroupMember member : members) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    String email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    String phone = piiCryptoService.decrypt(user.getId(), user.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("updaterUserId", updaterUserId);
                    metadata.put("updaterName", updaterName);

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.GROUP_UPDATED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Nhóm đã được cập nhật")
                            .message(String.format("%s đã cập nhật thông tin nhóm \"%s\"", updaterName, group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send group update notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send group update notifications: {}", ex.getMessage(), ex);
        }
    }

    private void sendGroupDeletedNotification(Group group, Long deleterUserId) {
        try {
            User deleter = userRepository.findById(deleterUserId).orElse(null);
            String deleterFullName = deleter != null ? piiCryptoService.decrypt(deleter.getId(), deleter.getFullName()) : null;
            String deleterName = !ObjectUtils.isEmpty(deleterFullName)
                    ? deleterFullName
                    : (deleter != null ? deleter.getUsername() : "Quản trị viên");

            List<GroupMember> members = groupMemberRepository.findByGroup(group);
            for (GroupMember member : members) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    String email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    String phone = piiCryptoService.decrypt(user.getId(), user.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("deleterUserId", deleterUserId);
                    metadata.put("deleterName", deleterName);

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.GROUP_DELETED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Nhóm đã bị xóa")
                            .message(String.format("%s đã xóa nhóm \"%s\"", deleterName, group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send group deleted notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send group deleted notifications: {}", ex.getMessage(), ex);
        }
    }

    private void sendMemberRemovedNotification(Group group, Long removedUserId, Long removerUserId) {
        try {
            User remover = userRepository.findById(removerUserId).orElse(null);
            String removerFullName = remover != null ? piiCryptoService.decrypt(remover.getId(), remover.getFullName()) : null;
            String removerName = !ObjectUtils.isEmpty(removerFullName)
                    ? removerFullName
                    : (remover != null ? remover.getUsername() : "Quản trị viên");

            // Send notification to the removed user
            try {
                User removedUser = userRepository.findById(removedUserId).orElse(null);
                if (removedUser != null) {
                    String fullName = piiCryptoService.decrypt(removedUser.getId(), removedUser.getFullName());
                    String email = piiCryptoService.decrypt(removedUser.getId(), removedUser.getEmail());
                    String phone = piiCryptoService.decrypt(removedUser.getId(), removedUser.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("removerUserId", removerUserId);
                    metadata.put("removerName", removerName);

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.MEMBER_REMOVED)
                            .userId(removedUser.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(removedUser.getUsername())
                            .source("group-service")
                            .title("Bạn đã bị xóa khỏi nhóm")
                            .message(String.format("%s đã xóa bạn khỏi nhóm \"%s\"", removerName, group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                }
            } catch (Exception e) {
                log.warn("Failed to send removal notification to removed user {}: {}", removedUserId, e.getMessage());
            }

            // Send notification to all remaining members
            List<GroupMember> remainingMembers = groupMemberRepository.findByGroup(group);
            String removedUserName = resolveUserFullName(removedUserId);
            for (GroupMember member : remainingMembers) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    String email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    String phone = piiCryptoService.decrypt(user.getId(), user.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("removedUserId", removedUserId);
                    metadata.put("removedUserName", removedUserName != null ? removedUserName : "Thành viên");
                    metadata.put("removerUserId", removerUserId);
                    metadata.put("removerName", removerName);

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.MEMBER_REMOVED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Thành viên đã bị xóa khỏi nhóm")
                            .message(String.format("%s đã xóa %s khỏi nhóm \"%s\"",
                                    removerName,
                                    removedUserName != null ? removedUserName : "thành viên",
                                    group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send member removed notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send member removed notifications: {}", ex.getMessage(), ex);
        }
    }

    private void sendMemberLeftNotification(Group group, Long leftUserId) {
        try {
            User leftUser = userRepository.findById(leftUserId).orElse(null);
            String leftUserFullName = leftUser != null ? piiCryptoService.decrypt(leftUser.getId(), leftUser.getFullName()) : null;
            String leftUserName = !ObjectUtils.isEmpty(leftUserFullName)
                    ? leftUserFullName
                    : (leftUser != null ? leftUser.getUsername() : "Thành viên");

            // Send notification to all remaining members
            List<GroupMember> remainingMembers = groupMemberRepository.findByGroup(group);
            for (GroupMember member : remainingMembers) {
                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    String email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    String phone = piiCryptoService.decrypt(user.getId(), user.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("leftUserId", leftUserId);
                    metadata.put("leftUserName", leftUserName);

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.MEMBER_LEFT)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Thành viên đã rời nhóm")
                            .message(String.format("%s đã rời khỏi nhóm \"%s\"", leftUserName, group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send member left notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send member left notifications: {}", ex.getMessage(), ex);
        }
    }

    private void sendMemberRoleChangedNotification(Group group, Long targetUserId, GroupRole oldRole, GroupRole newRole, Long changerUserId) {
        try {
            User changer = userRepository.findById(changerUserId).orElse(null);
            String changerFullName = changer != null ? piiCryptoService.decrypt(changer.getId(), changer.getFullName()) : null;
            String changerName = !ObjectUtils.isEmpty(changerFullName)
                    ? changerFullName
                    : (changer != null ? changer.getUsername() : "Quản trị viên");

            String targetUserName = resolveUserFullName(targetUserId);

            // Send notification to the affected member
            try {
                User targetUser = userRepository.findById(targetUserId).orElse(null);
                if (targetUser != null) {
                    String fullName = piiCryptoService.decrypt(targetUser.getId(), targetUser.getFullName());
                    String email = piiCryptoService.decrypt(targetUser.getId(), targetUser.getEmail());
                    String phone = piiCryptoService.decrypt(targetUser.getId(), targetUser.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("changerUserId", changerUserId);
                    metadata.put("changerName", changerName);
                    metadata.put("oldRole", oldRole.name());
                    metadata.put("newRole", newRole.name());

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.MEMBER_ROLE_CHANGED)
                            .userId(targetUser.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(targetUser.getUsername())
                            .source("group-service")
                            .title("Vai trò của bạn đã thay đổi")
                            .message(String.format("%s đã thay đổi vai trò của bạn từ %s thành %s trong nhóm \"%s\"",
                                    changerName, oldRole.name(), newRole.name(), group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                }
            } catch (Exception e) {
                log.warn("Failed to send role change notification to target user {}: {}", targetUserId, e.getMessage());
            }

            // Send notification to all other members
            List<GroupMember> members = groupMemberRepository.findByGroup(group);
            for (GroupMember member : members) {
                if (member.getUserId().equals(targetUserId)) continue; // Skip the target user (already notified)

                try {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    if (user == null) continue;

                    String fullName = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    String email = piiCryptoService.decrypt(user.getId(), user.getEmail());
                    String phone = piiCryptoService.decrypt(user.getId(), user.getPhone());

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("groupId", group.getId());
                    metadata.put("groupName", group.getName());
                    metadata.put("targetUserId", targetUserId);
                    metadata.put("targetUserName", targetUserName != null ? targetUserName : "Thành viên");
                    metadata.put("changerUserId", changerUserId);
                    metadata.put("changerName", changerName);
                    metadata.put("oldRole", oldRole.name());
                    metadata.put("newRole", newRole.name());

                    UserNotificationEvent event = UserNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(UserNotificationEvent.MEMBER_ROLE_CHANGED)
                            .userId(user.getId())
                            .email(email)
                            .fullName(fullName)
                            .phone(phone)
                            .username(user.getUsername())
                            .source("group-service")
                            .title("Vai trò thành viên đã thay đổi")
                            .message(String.format("%s đã thay đổi vai trò của %s từ %s thành %s trong nhóm \"%s\"",
                                    changerName,
                                    targetUserName != null ? targetUserName : "thành viên",
                                    oldRole.name(),
                                    newRole.name(),
                                    group.getName()))
                            .timestamp(LocalDateTime.now())
                            .additionalData(metadata)
                            .build();
                    kafkaProducerService.sendNotificationEvent(event);
                } catch (Exception e) {
                    log.warn("Failed to send role change notification to user {}: {}", member.getUserId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to send member role changed notifications: {}", ex.getMessage(), ex);
        }
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
}


