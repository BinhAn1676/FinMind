package com.finance.userservice.repository;

import com.finance.userservice.constant.GroupInviteStatus;
import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    Optional<GroupInvitation> findByGroupAndInviteeUserId(Group group, Long inviteeUserId);
    Optional<GroupInvitation> findByIdAndGroupId(Long id, Long groupId);
    List<GroupInvitation> findByGroupAndInviteeUserIdIn(Group group, List<Long> inviteeIds);
    Page<GroupInvitation> findByGroupAndStatus(Group group, GroupInviteStatus status, Pageable pageable);
    List<GroupInvitation> findByInviterUserIdAndGroup(Long inviterUserId, Group group);
    @Modifying
    void deleteByGroup(Group group);
}


