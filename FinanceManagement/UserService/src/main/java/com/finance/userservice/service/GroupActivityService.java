package com.finance.userservice.service;

import com.finance.userservice.constant.GroupActivityType;
import com.finance.userservice.dto.group.GroupActivityDto;
import com.finance.userservice.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;

public interface GroupActivityService {

    void logActivity(Group group,
                     Long actorUserId,
                     String actorName,
                     GroupActivityType type,
                     String message,
                     Map<String, Object> metadata);

    Page<GroupActivityDto> listActivities(Long groupId,
                                          String keyword,
                                          GroupActivityType type,
                                          Instant from,
                                          Instant to,
                                          Pageable pageable);
}



