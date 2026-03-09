package com.finance.userservice.service;

import com.finance.userservice.dto.group.GroupAccountDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GroupAccountService {
    Page<GroupAccountDto> list(Long groupId, String search, Pageable pageable);
    GroupAccountDto link(Long groupId, Long accountId);
    void unlink(Long groupId, Long accountId);
}













