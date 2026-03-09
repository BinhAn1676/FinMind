package com.finance.userservice.repository;

import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupActivity;
import com.finance.userservice.constant.GroupActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface GroupActivityRepository extends JpaRepository<GroupActivity, Long> {

    Page<GroupActivity> findByGroupOrderByCreatedAtDesc(Group group, Pageable pageable);

    @Query("""
            SELECT a
            FROM GroupActivity a
            WHERE a.group = :group
              AND (:type IS NULL OR a.type = :type)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
              AND (
                    :keyword IS NULL OR :keyword = '' OR
                    LOWER(COALESCE(a.actorName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                    LOWER(COALESCE(a.message, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                    LOWER(COALESCE(a.metadata, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            ORDER BY a.createdAt DESC
            """)
    Page<GroupActivity> searchActivities(@Param("group") Group group,
                                         @Param("keyword") String keyword,
                                         @Param("type") GroupActivityType type,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);

    @Modifying
    void deleteByGroup(Group group);
}



