package com.finance.userservice.service;

import com.finance.userservice.constant.GroupInviteStatus;
import com.finance.userservice.dto.group.GroupCreateRequest;
import com.finance.userservice.dto.group.GroupDto;
import com.finance.userservice.dto.group.GroupInviteDto;
import com.finance.userservice.dto.group.GroupUpdateRequest;
import com.finance.userservice.dto.group.GroupDto.MemberDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GroupService {
    GroupDto create(GroupCreateRequest request);
    Page<GroupDto> search(String query, Pageable pageable);
    GroupDto getById(Long id);
    GroupDto update(Long id, GroupUpdateRequest request);
    GroupDto updateAvatar(Long id, MultipartFile avatarFile);
    List<GroupInviteDto> inviteMembers(Long groupId, List<Long> inviteeIds);
    GroupInviteDto respondToInvite(Long groupId, Long inviteId, boolean accept);
    Page<GroupInviteDto> getInvites(Long groupId, GroupInviteStatus status, Pageable pageable);
    Page<MemberDto> getMembers(Long groupId, Pageable pageable);
    GroupInviteDto cancelInvite(Long groupId, Long inviteId);
    MemberDto updateMemberRole(Long groupId, Long userId, com.finance.userservice.dto.group.UpdateMemberRoleRequest request);
    void removeMember(Long groupId, Long userId);
    void delete(Long id);
    void leaveGroup(Long groupId);
}


