package com.finance.userservice.controller;

import com.finance.userservice.constant.GroupActivityType;
import com.finance.userservice.constant.GroupInviteStatus;
import com.finance.userservice.dto.group.*;
import com.finance.userservice.service.GroupActivityService;
import com.finance.userservice.service.GroupService;
import com.finance.userservice.service.GroupAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
@Validated
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupAccountService groupAccountService;
    private final GroupActivityService groupActivityService;
    private final com.finance.userservice.repository.GroupRepository groupRepository;

    @GetMapping
    public ResponseEntity<Page<GroupDto>> search(@RequestParam(value = "q", required = false) String query,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupService.search(query, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(groupService.getById(id));
    }

    @PostMapping
    public ResponseEntity<GroupDto> create(@RequestBody @Validated GroupCreateRequest request) {
        return ResponseEntity.ok(groupService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> update(@PathVariable("id") Long id,
                                           @RequestBody @Validated GroupUpdateRequest request) {
        return ResponseEntity.ok(groupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable("id") Long id) {
        groupService.leaveGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GroupDto> updateAvatar(@PathVariable("id") Long id,
                                                 @RequestPart("avatar") MultipartFile avatar) {
        return ResponseEntity.ok(groupService.updateAvatar(id, avatar));
    }

    @PostMapping("/{id}/invites")
    public ResponseEntity<List<GroupInviteDto>> invite(@PathVariable("id") Long id,
                                                       @RequestBody @Validated GroupInviteRequest request) {
        return ResponseEntity.ok(groupService.inviteMembers(id, request.getInviteeUserIds()));
    }

    @GetMapping("/{id}/invites")
    public ResponseEntity<Page<GroupInviteDto>> listInvites(@PathVariable("id") Long id,
                                                            @RequestParam(value = "status", required = false) GroupInviteStatus status,
                                                            @RequestParam(value = "page", defaultValue = "0") int page,
                                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupService.getInvites(id, status, pageable));
    }

    @PostMapping("/{id}/invites/{inviteId}/accept")
    public ResponseEntity<GroupInviteDto> accept(@PathVariable("id") Long id, @PathVariable("inviteId") Long inviteId) {
        return ResponseEntity.ok(groupService.respondToInvite(id, inviteId, true));
    }

    @PostMapping("/{id}/invites/{inviteId}/reject")
    public ResponseEntity<GroupInviteDto> reject(@PathVariable("id") Long id, @PathVariable("inviteId") Long inviteId) {
        return ResponseEntity.ok(groupService.respondToInvite(id, inviteId, false));
    }

    @PostMapping("/{id}/invites/{inviteId}/cancel")
    public ResponseEntity<GroupInviteDto> cancel(@PathVariable("id") Long id, @PathVariable("inviteId") Long inviteId) {
        return ResponseEntity.ok(groupService.cancelInvite(id, inviteId));
    }

    @GetMapping("/{id}/accounts")
    public ResponseEntity<Page<GroupAccountDto>> listAccounts(@PathVariable("id") Long id,
                                                              @RequestParam(value = "q", required = false) String query,
                                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                                              @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupAccountService.list(id, query, pageable));
    }

    @PostMapping("/{id}/accounts")
    public ResponseEntity<GroupAccountDto> linkAccount(@PathVariable("id") Long id,
                                                       @RequestBody @Validated GroupAccountLinkRequest request) {
        return ResponseEntity.ok(groupAccountService.link(id, request.getAccountId()));
    }

    @DeleteMapping("/{id}/accounts/{accountId}")
    public ResponseEntity<Void> unlinkAccount(@PathVariable("id") Long id, @PathVariable("accountId") Long accountId) {
        groupAccountService.unlink(id, accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activities")
    public ResponseEntity<Page<GroupActivityDto>> listActivities(@PathVariable("id") Long id,
                                                                 @RequestParam(value = "q", required = false) String query,
                                                                 @RequestParam(value = "type", required = false) GroupActivityType type,
                                                                 @RequestParam(value = "from", required = false) String from,
                                                                 @RequestParam(value = "to", required = false) String to,
                                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);

        Instant fromInstant = null;
        Instant toInstant = null;

        if (from != null && !from.isBlank()) {
            LocalDate fromDate = LocalDate.parse(from);
            fromInstant = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (to != null && !to.isBlank()) {
            // Lấy cuối ngày (23:59:59.999999999) để filter bao trọn ngày "to"
            LocalDate toDate = LocalDate.parse(to);
            toInstant = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).toInstant();
        }

        return ResponseEntity.ok(groupActivityService.listActivities(id, query, type, fromInstant, toInstant, pageable));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<Page<GroupDto.MemberDto>> listMembers(@PathVariable("id") Long id,
                                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                                @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(groupService.getMembers(id, pageable));
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<GroupDto.MemberDto> updateMemberRole(@PathVariable("id") Long id,
                                                                @PathVariable("userId") Long userId,
                                                                @RequestBody @Validated com.finance.userservice.dto.group.UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(groupService.updateMemberRole(id, userId, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable("id") Long id,
                                              @PathVariable("userId") Long userId) {
        groupService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activities/log")
    public ResponseEntity<Void> logActivity(@PathVariable("id") Long id,
                                            @RequestBody @Validated com.finance.userservice.dto.group.LogActivityRequest request) {
        com.finance.userservice.entity.Group group = groupRepository.findById(id)
                .orElseThrow(() -> new com.finance.userservice.exception.ResourceNotFoundException("Group", "Id", String.valueOf(id)));
        
        groupActivityService.logActivity(
                group,
                request.getActorUserId(),
                request.getActorName(),
                request.getType(),
                request.getMessage(),
                request.getMetadata()
        );
        return ResponseEntity.ok().build();
    }
}


