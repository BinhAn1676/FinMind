package com.finance.userservice.repository;

import com.finance.userservice.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("""
            select distinct g from Group g
            join GroupMember gm on gm.group = g
            where gm.userId = :userId
              and (:q is null or lower(g.name) like lower(concat('%', :q, '%')))
            """)
    Page<Group> searchByMember(@Param("q") String query, @Param("userId") Long userId, Pageable pageable);
}


