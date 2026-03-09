package com.finance.financeservice.service;

import com.finance.financeservice.mysql.entity.Account;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AccountService {
    Optional<Account> getById(Long id);
    List<Account> getByIds(List<Long> ids);
    Page<Account> filter(String userId, String textSearch, int page, int size);
    Account createAccount(String userId, String accountNumber, String accountHolderName, 
                         String bankShortName, String bankFullName, String bankCode, 
                         String label, String accumulated);
    Account updateAccount(Long id, String label, String accumulated);
    void deleteAccount(Long id);
    AccountSummary summary(String userId);
    SyncSupport syncSupport();
    List<AccountDistribution> getAccountDistribution(String userId);

    record AccountSummary(BigDecimal totalBalance, long accountCount) {}
    record SyncSupport(Set<String> autoSyncBanks) {}
    record AccountDistribution(String accountId, String label, String bankName, BigDecimal balance, double percentage) {}
}


