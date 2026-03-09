package com.finance.userservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.userservice.constant.GroupActivityType;
import com.finance.userservice.dto.group.GroupActivityDto;
import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupActivity;
import com.finance.userservice.entity.User;
import com.finance.userservice.repository.GroupActivityRepository;
import com.finance.userservice.repository.GroupRepository;
import com.finance.userservice.service.GroupActivityService;
import com.finance.userservice.repository.UserRepository;
import com.finance.userservice.service.crypto.PiiCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupActivityServiceImpl implements GroupActivityService {

    private final GroupRepository groupRepository;
    private final GroupActivityRepository groupActivityRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PiiCryptoService piiCryptoService;

    @Override
    @Transactional
    public void logActivity(Group group,
                            Long actorUserId,
                            String actorName,
                            GroupActivityType type,
                            String message,
                            Map<String, Object> metadata) {
        if (group == null || actorUserId == null || type == null) {
            return;
        }

        GroupActivity activity = new GroupActivity();
        activity.setGroup(group);
        activity.setActorUserId(actorUserId);
        if (ObjectUtils.isEmpty(actorName)) {
            // Resolve actor's full name from user info if not provided
            try {
                User user = userRepository.findById(actorUserId).orElse(null);
                if (user != null && user.getFullName() != null) {
                    String decrypted = piiCryptoService.decrypt(user.getId(), user.getFullName());
                    activity.setActorName(decrypted);
                } else {
                    activity.setActorName(null);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve actorName for user {}: {}", actorUserId, e.getMessage());
                activity.setActorName(null);
            }
        } else {
            activity.setActorName(actorName);
        }
        activity.setType(type);
        activity.setMessage(message);

        if (metadata != null && !metadata.isEmpty()) {
            try {
                activity.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize group activity metadata: {}", e.getMessage());
            }
        }

        groupActivityRepository.save(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupActivityDto> listActivities(Long groupId,
                                                 String keyword,
                                                 GroupActivityType type,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        Group group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return Page.empty(pageable);
        }

        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;
        if (from != null) {
            fromDate = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        }
        if (to != null) {
            toDate = LocalDateTime.ofInstant(to, ZoneOffset.UTC);
        }

        Page<GroupActivity> page = groupActivityRepository.searchActivities(
                group,
                trimmedKeyword,
                type,
                fromDate,
                toDate,
                pageable
        );

        return page.map(a -> GroupActivityDto.builder()
                .id(a.getId())
                .groupId(a.getGroup().getId())
                .actorUserId(a.getActorUserId())
                .actorName(a.getActorName())
                .type(a.getType())
                .message(a.getMessage())
                .metadata(a.getMetadata())
                .createdAt(a.getCreatedAt())
                .build());
    }
}


