package com.finance.userservice.repository;

import com.finance.userservice.entity.Group;
import com.finance.userservice.entity.GroupAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface GroupAccountRepository extends JpaRepository<GroupAccount, Long> {

    /**
     * Find all group accounts for a group.
     * Note: Search by account details (label, accountNumber, bankBrandName) is no longer
     * supported at database level since these fields are now fetched real-time from FinanceService.
     * The 'q' parameter is kept for backward compatibility but is ignored.
     */
    @Query("""
            select ga from GroupAccount ga
            where ga.group = :group
            """)
    Page<GroupAccount> searchByGroup(@Param("group") Group group,
                                     @Param("q") String query,
                                     Pageable pageable);

    Page<GroupAccount> findByGroup(Group group, Pageable pageable);

    Optional<GroupAccount> findByGroupAndAccountId(Group group, Long accountId);
    List<GroupAccount> findByGroupAndOwnerUserId(Group group, Long ownerUserId);
    @Modifying
    void deleteByGroup(Group group);
}











