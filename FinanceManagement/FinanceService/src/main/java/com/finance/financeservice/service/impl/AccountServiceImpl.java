package com.finance.financeservice.service.impl;

import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.mysql.repository.AccountRepository;
import com.finance.financeservice.service.AccountService;
import com.finance.financeservice.service.crypto.PiiCryptoService;
import com.finance.financeservice.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final PiiCryptoService piiCryptoService;
    // Banks that automatically sync balance; accumulated should not be edited manually
    private static final Set<String> AUTO_SYNC_BANKS = Set.of("TPBank", "VietinBank");

    @Override
    public Optional<Account> getById(Long id) {
        return accountRepository.findById(id).map(piiCryptoService::decryptAccount);
    }

    @Override
    public List<Account> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return accountRepository.findAllById(ids).stream()
                .map(piiCryptoService::decryptAccount)
                .toList();
    }

    @Override
    public Page<Account> filter(String userId, String textSearch, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String hashed = (textSearch == null || textSearch.isBlank()) ? null : HashUtils.sha256(textSearch);
        Page<Account> pageResult = accountRepository.searchAccounts(userId, textSearch, hashed, pageable);
        List<Account> decrypted = pageResult.getContent().stream().map(piiCryptoService::decryptAccount).toList();
        return new org.springframework.data.domain.PageImpl<>(decrypted, pageable, pageResult.getTotalElements());
    }

    @Override
    public Account createAccount(String userId, String accountNumber, String accountHolderName,
                                 String bankShortName, String bankFullName, String bankCode,
                                 String label, String accumulated) {
        Account account = new Account();
        account.setUserId(userId);
        account.setLabel(label);
        account.setAccumulated(accumulated != null ? accumulated : "0");
        account.setActive("1");
        
        // Encrypt and hash PII fields
        String encAccountNumber = piiCryptoService.encrypt(userId, accountNumber);
        String encAccountHolderName = piiCryptoService.encrypt(userId, accountHolderName);
        String encBankShortName = piiCryptoService.encrypt(userId, bankShortName);
        String encBankFullName = piiCryptoService.encrypt(userId, bankFullName);
        String encBankCode = piiCryptoService.encrypt(userId, bankCode);
        
        account.setAccountNumber(encAccountNumber != null ? encAccountNumber : accountNumber);
        account.setAccountNumberHash(HashUtils.sha256(accountNumber));
        account.setAccountHolderName(encAccountHolderName != null ? encAccountHolderName : accountHolderName);
        account.setAccountHolderNameHash(HashUtils.sha256(accountHolderName));
        account.setBankShortName(encBankShortName != null ? encBankShortName : bankShortName);
        account.setBankShortNameHash(HashUtils.sha256(bankShortName));
        account.setBankFullName(encBankFullName != null ? encBankFullName : bankFullName);
        account.setBankFullNameHash(HashUtils.sha256(bankFullName));
        account.setBankCode(encBankCode != null ? encBankCode : bankCode);
        account.setBankCodeHash(HashUtils.sha256(bankCode));
        
        // Generate a unique bankAccountId for manual accounts
        account.setBankAccountId("MANUAL_" + System.currentTimeMillis() + "_" + userId);
        
        return accountRepository.save(account);
    }

    @Override
    public Account updateAccount(Long id, String label, String accumulated) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (label != null) {
            account.setLabel(label);
        }
        // Only allow updating accumulated when bank is NOT auto-syncing balance
        if (accumulated != null && !isAutoSync(account)) {
            account.setAccumulated(accumulated);
        }
        return accountRepository.save(account);
    }

    @Override
    public void deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new IllegalArgumentException("Account not found with id: " + id);
        }
        accountRepository.deleteById(id);
    }

    @Override
    public AccountSummary summary(String userId) {
        // Compute total balance and count
        Page<Account> all = accountRepository.searchAccounts(userId, null, null, Pageable.unpaged());
        List<Account> decrypted = all.getContent().stream().map(piiCryptoService::decryptAccount).toList();
        BigDecimal total = decrypted.stream()
                .map(a -> {
                    try {
                        return new BigDecimal(a.getAccumulated() == null ? "0" : a.getAccumulated());
                    } catch (NumberFormatException e) {
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountSummary(total, all.getTotalElements());
    }

    @Override
    public SyncSupport syncSupport() {
        return new SyncSupport(AUTO_SYNC_BANKS);
    }

    @Override
    public List<AccountDistribution> getAccountDistribution(String userId) {
        Page<Account> all = accountRepository.searchAccounts(userId, null, null, Pageable.unpaged());
        List<Account> accounts = all.getContent().stream().map(piiCryptoService::decryptAccount).toList();
        
        BigDecimal total = accounts.stream()
                .map(a -> {
                    try {
                        return new BigDecimal(a.getAccumulated() == null ? "0" : a.getAccumulated());
                    } catch (NumberFormatException e) {
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return accounts.stream()
                    .map(a -> new AccountDistribution(
                            a.getId().toString(),
                            a.getLabel() != null ? a.getLabel() : (a.getBankShortName() != null ? a.getBankShortName() : a.getBankFullName()),
                            a.getBankShortName() != null ? a.getBankShortName() : a.getBankFullName(),
                            BigDecimal.ZERO,
                            0.0
                    ))
                    .toList();
        }

        return accounts.stream()
                .map(a -> {
                    BigDecimal balance;
                    try {
                        balance = new BigDecimal(a.getAccumulated() == null ? "0" : a.getAccumulated());
                    } catch (NumberFormatException e) {
                        balance = BigDecimal.ZERO;
                    }
                    double percentage = balance.divide(total, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .doubleValue();
                    return new AccountDistribution(
                            a.getId().toString(),
                            a.getLabel() != null ? a.getLabel() : (a.getBankShortName() != null ? a.getBankShortName() : a.getBankFullName()),
                            a.getBankShortName() != null ? a.getBankShortName() : a.getBankFullName(),
                            balance,
                            percentage
                    );
                })
                .toList();
    }

    private boolean isAutoSync(Account account) {
        String name = account.getBankShortName() != null && !account.getBankShortName().isBlank()
                ? account.getBankShortName() : account.getBankFullName();
        return name != null && AUTO_SYNC_BANKS.contains(name);
    }
}
