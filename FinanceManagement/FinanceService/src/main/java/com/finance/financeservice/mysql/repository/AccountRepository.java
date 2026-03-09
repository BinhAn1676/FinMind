package com.finance.financeservice.mysql.repository;

import com.finance.financeservice.mysql.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account findByBankAccountIdAndUserId(String bankAccountId, String userId);

    List<Account> findByAccountNumberHash(String accountNumberHash);

    @Query("SELECT a FROM Account a " +
            "WHERE (:userId IS NULL OR a.userId = :userId) AND " +
            "(:text IS NULL OR :text = '' OR " +
            "LOWER(a.label) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "(:textHash IS NOT NULL AND (" +
            "a.bankShortNameHash = :textHash OR " +
            "a.bankFullNameHash = :textHash OR " +
            "a.accountNumberHash = :textHash OR " +
            "a.accountHolderNameHash = :textHash OR " +
            "a.bankBinHash = :textHash OR " +
            "a.bankCodeHash = :textHash)))")
    Page<Account> searchAccounts(@Param("userId") String userId,
                                 @Param("text") String text,
                                 @Param("textHash") String textHash,
                                 Pageable pageable);
}
