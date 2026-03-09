package com.finance.financeservice.service;

import com.finance.financeservice.mysql.entity.ExternalAccount;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExternalAccountService {
    Optional<ExternalAccount> getById(Long id);
    Page<ExternalAccount> filter(String userId, String textSearch, int page, int size);
    ExternalAccount createExternalAccount(String userId, String label, String type, String accumulated, String description);
    ExternalAccount updateExternalAccount(Long id, String label, String type, String accumulated, String description);
    void deleteExternalAccount(Long id);
    ExternalAccountSummary summary(String userId);
    List<ExternalAccountDistribution> getExternalAccountDistribution(String userId);

    record ExternalAccountSummary(BigDecimal totalBalance, long accountCount) {}
    record ExternalAccountDistribution(String accountId, String label, String type, BigDecimal balance, double percentage) {}
}

