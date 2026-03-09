package com.finance.financeservice.service.impl;

import com.finance.financeservice.mysql.entity.ExternalAccount;
import com.finance.financeservice.mysql.repository.ExternalAccountRepository;
import com.finance.financeservice.service.ExternalAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExternalAccountServiceImpl implements ExternalAccountService {
    private final ExternalAccountRepository externalAccountRepository;

    @Override
    public Optional<ExternalAccount> getById(Long id) {
        return externalAccountRepository.findById(id);
    }

    @Override
    public Page<ExternalAccount> filter(String userId, String textSearch, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return externalAccountRepository.searchExternalAccounts(userId, textSearch, pageable);
    }

    @Override
    public ExternalAccount createExternalAccount(String userId, String label, String type, String accumulated, String description) {
        ExternalAccount account = new ExternalAccount();
        account.setUserId(userId);
        account.setLabel(label);
        account.setType(type);
        account.setAccumulated(accumulated != null ? accumulated : "0");
        account.setDescription(description);
        return externalAccountRepository.save(account);
    }

    @Override
    public ExternalAccount updateExternalAccount(Long id, String label, String type, String accumulated, String description) {
        ExternalAccount account = externalAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("External account not found"));
        if (label != null) {
            account.setLabel(label);
        }
        if (type != null) {
            account.setType(type);
        }
        if (accumulated != null) {
            account.setAccumulated(accumulated);
        }
        if (description != null) {
            account.setDescription(description);
        }
        return externalAccountRepository.save(account);
    }

    @Override
    public void deleteExternalAccount(Long id) {
        if (!externalAccountRepository.existsById(id)) {
            throw new IllegalArgumentException("External account not found with id: " + id);
        }
        externalAccountRepository.deleteById(id);
    }

    @Override
    public ExternalAccountSummary summary(String userId) {
        Page<ExternalAccount> all = externalAccountRepository.searchExternalAccounts(userId, null, Pageable.unpaged());
        BigDecimal total = all.stream()
                .map(a -> {
                    try {
                        return new BigDecimal(a.getAccumulated() == null ? "0" : a.getAccumulated());
                    } catch (NumberFormatException e) {
                        return BigDecimal.ZERO;
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ExternalAccountSummary(total, all.getTotalElements());
    }

    @Override
    public List<ExternalAccountDistribution> getExternalAccountDistribution(String userId) {
        Page<ExternalAccount> all = externalAccountRepository.searchExternalAccounts(userId, null, Pageable.unpaged());
        List<ExternalAccount> accounts = all.getContent();
        
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
                    .map(a -> new ExternalAccountDistribution(
                            a.getId().toString(),
                            a.getLabel(),
                            a.getType(),
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
                    return new ExternalAccountDistribution(
                            a.getId().toString(),
                            a.getLabel(),
                            a.getType(),
                            balance,
                            percentage
                    );
                })
                .toList();
    }
}

