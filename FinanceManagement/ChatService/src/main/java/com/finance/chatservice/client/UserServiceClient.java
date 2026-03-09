package com.finance.chatservice.client;

import com.finance.chatservice.dto.GroupAccountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign Client for UserService integration.
 *
 * Used by ChatService to resolve group-linked bank accounts
 * when @AI is mentioned in a group chat.
 *
 * Uses Eureka service discovery - no hardcoded URL.
 */
@FeignClient(name = "users")
public interface UserServiceClient {

    /**
     * Get accounts linked to a group.
     *
     * @param groupId Group ID
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Page of group accounts containing bankAccountId fields
     */
    @GetMapping("/api/v1/groups/{groupId}/accounts")
    ResponseEntity<Map<String, Object>> getGroupAccounts(
        @PathVariable("groupId") Long groupId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "100") int size
    );
}
