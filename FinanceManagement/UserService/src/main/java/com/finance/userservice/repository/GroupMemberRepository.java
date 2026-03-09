package com.finance.userservice.repository;

import com.finance.userservice.constant.GroupRole;
import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByGroup(Group group);
    Page<GroupMember> findByGroup(Group group, Pageable pageable);
    long countByGroup(Group group);
    Optional<GroupMember> findByGroupAndUserId(Group group, Long userId);
    long countByGroupAndRole(Group group, GroupRole role);
    @Modifying
    void deleteByGroup(Group group);
}


