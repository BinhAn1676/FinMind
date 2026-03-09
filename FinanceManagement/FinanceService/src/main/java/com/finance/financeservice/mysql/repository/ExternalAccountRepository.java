package com.finance.financeservice.mysql.repository;

import com.finance.financeservice.mysql.entity.ExternalAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {
    
    @Query("SELECT e FROM ExternalAccount e " +
            "WHERE (:userId IS NULL OR e.userId = :userId) AND " +
            "(:text IS NULL OR :text = '' OR " +
            "LOWER(e.label) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(e.type) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(e.description) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<ExternalAccount> searchExternalAccounts(@Param("userId") String userId,
                                                  @Param("text") String text,
                                                  Pageable pageable);
}

