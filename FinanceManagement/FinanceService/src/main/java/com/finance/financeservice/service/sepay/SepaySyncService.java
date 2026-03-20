package com.finance.financeservice.service.sepay;

import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.service.client.KeyManagementServiceClient;
import com.finance.financeservice.service.client.UserServiceClient;
import com.finance.financeservice.config.SepayApiProperties;
import com.finance.financeservice.dto.PageResponse;
import com.finance.financeservice.dto.keys.DecryptRequest;
import com.finance.financeservice.dto.keys.DecryptResponse;
import com.finance.financeservice.dto.keys.EncryptRequest;
import com.finance.financeservice.dto.keys.EncryptResponse;
import com.finance.financeservice.dto.sepay.*;
import com.finance.financeservice.dto.sepay.oauth2.OAuth2BankAccountDto;
import com.finance.financeservice.dto.sepay.oauth2.OAuth2TransactionDto;
import com.finance.financeservice.dto.user.UserDto;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.TransactionRepository;
import com.finance.financeservice.service.PlanningBudgetService;
import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.mysql.repository.AccountRepository;
import com.finance.financeservice.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class SepaySyncService {

    private final UserServiceClient userServiceClient;
    private final KeyManagementServiceClient keyManagementServiceClient;
    private final SepayApiProperties sepayApiProperties;
    private final SepayOAuth2Properties oauth2Properties;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PlanningBudgetService planningBudgetService;
    private final SepayOAuth2TokenService oauth2TokenService;
    private final SepayWebhookService webhookService;
    private final RestTemplate restTemplate = new RestTemplate();

    public SepaySyncService(
            UserServiceClient userServiceClient,
            KeyManagementServiceClient keyManagementServiceClient,
            SepayApiProperties sepayApiProperties,
            SepayOAuth2Properties oauth2Properties,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PlanningBudgetService planningBudgetService,
            @Lazy SepayOAuth2TokenService oauth2TokenService,
            @Lazy SepayWebhookService webhookService) {
        this.userServiceClient = userServiceClient;
        this.keyManagementServiceClient = keyManagementServiceClient;
        this.sepayApiProperties = sepayApiProperties;
        this.oauth2Properties = oauth2Properties;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.planningBudgetService = planningBudgetService;
        this.oauth2TokenService = oauth2TokenService;
        this.webhookService = webhookService;
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void syncAllAccounts() {
        log.info("Starting syncAllAccounts job");
        forEachUserWithToken(this::syncAccountsForUser);
    }

    public void syncAllTransactions() {
        log.info("Starting syncAllTransactions job");
        forEachUserWithToken(this::syncTransactionsForUser);
    }

    public void syncAccountsForUser(String userId) {
        String decryptedToken = getDecryptedToken(userId);
        if (decryptedToken == null) {
            log.warn("No bank token for user {} - skipping accounts sync", userId);
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", withBearer(decryptedToken));
        headers.set("Content-Type", "application/json");
        try{
            ResponseEntity<AccountListResponse> response = restTemplate.exchange(
                    sepayApiProperties.getAccountsUrl(), HttpMethod.GET, new HttpEntity<>(headers), AccountListResponse.class);
            AccountListResponse body = response.getBody();
            if (body == null || body.getBankaccounts() == null) {
                log.warn("No accounts returned for user {}", userId);
                return;
            }
            for (BankAccountDto dto : body.getBankaccounts()) {
                upsertAccount(dto, userId);
            }
            log.info("Accounts synced for user {}: {} accounts", userId, body.getBankaccounts().size());
        }catch (Exception e){
            log.error("Error syncing accounts for user {}", userId, e);
            return;
        }
    }

    public void syncTransactionsForUser(String userId) {
        String decryptedToken = getDecryptedToken(userId);
        if (decryptedToken == null) {
            log.warn("No bank token for user {} - skipping transactions sync", userId);
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", withBearer(decryptedToken));
        headers.set("Content-Type", "application/json");
        ResponseEntity<TransactionsListResponse> response = restTemplate.exchange(
                sepayApiProperties.getTransactionsUrl(), HttpMethod.GET, new HttpEntity<>(headers), TransactionsListResponse.class);
        TransactionsListResponse body = response.getBody();
        if (body == null || body.getTransactions() == null) {
            log.warn("No transactions returned for user {}", userId);
            return;
        }
        int saved = 0;
        for (TransactionDto dto : body.getTransactions()) {
            if (!transactionRepository.existsById(dto.getId())) {
                Transaction t = mapTransaction(dto, userId);
                transactionRepository.save(t);
                // reflect expense into planning budgets when category is available
                if (t.getAmountOut() != null && t.getAmountOut() > 0 
                        && t.getTransactionDate() != null 
                        && t.getCategory() != null 
                        && !"không xác định".equalsIgnoreCase(t.getCategory())) {
                    planningBudgetService.addExpense(userId, t.getCategory(), t.getAmountOut(), t.getTransactionDate().toLocalDate());
                }
                // Update account accumulated by applying transaction delta (in - out)
                updateAccountAccumulated(t.getBankAccountId(), userId, t.getAmountIn(), t.getAmountOut());
                saved++;
            }
        }
        log.info("Transactions synced for user {}: {} new transactions", userId, saved);
    }

    // API variants that return results without changing job behavior
    public SyncResult syncAccountsForUserApi(String userId) {
        String decryptedToken = getDecryptedToken(userId);
        if (decryptedToken == null) {
            return SyncResult.builder().success(false).message("No bank token available").processedCount(0).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", withBearer(decryptedToken));
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        try {
            ResponseEntity<AccountListResponse> response = restTemplate.exchange(
                    sepayApiProperties.getAccountsUrl(), HttpMethod.GET, new HttpEntity<>(headers), AccountListResponse.class);
            AccountListResponse body = response.getBody();
            if (body == null || body.getBankaccounts() == null) {
                return SyncResult.builder().success(true).message("No accounts returned").processedCount(0).build();
            }
            for (BankAccountDto dto : body.getBankaccounts()) {
                upsertAccount(dto, userId);
            }
            return SyncResult.builder().success(true).message("OK").processedCount(body.getBankaccounts().size()).build();
        } catch (Exception e) {
            return SyncResult.builder().success(false).message(e.getMessage()).processedCount(0).build();
        }
    }

    public SyncResult syncTransactionsForUserApi(String userId) {
        String decryptedToken = getDecryptedToken(userId);
        if (decryptedToken == null) {
            return SyncResult.builder().success(false).message("No bank token available").processedCount(0).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", withBearer(decryptedToken));
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        try {
            ResponseEntity<TransactionsListResponse> response = restTemplate.exchange(
                    sepayApiProperties.getTransactionsUrl(), HttpMethod.GET, new HttpEntity<>(headers), TransactionsListResponse.class);
            TransactionsListResponse body = response.getBody();
            if (body == null || body.getTransactions() == null) {
                return SyncResult.builder().success(true).message("No transactions returned").processedCount(0).build();
            }
            int saved = 0;
            for (TransactionDto dto : body.getTransactions()) {
                if (!transactionRepository.existsById(dto.getId())) {
                    Transaction t = mapTransaction(dto, userId);
                    transactionRepository.save(t);
                    // Update account accumulated by applying transaction delta (in - out)
                    updateAccountAccumulated(t.getBankAccountId(), userId, t.getAmountIn(), t.getAmountOut());
                    saved++;
                }
            }
            return SyncResult.builder().success(true).message("OK").processedCount(saved).build();
        } catch (Exception e) {
            return SyncResult.builder().success(false).message(e.getMessage()).processedCount(0).build();
        }
    }

    private String withBearer(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (!trimmed.toLowerCase().startsWith("bearer ")) {
            return "Bearer " + trimmed;
        }
        return trimmed;
    }

    private void forEachUserWithToken(java.util.function.Consumer<String> consumer) {
        int page = 0;
        int size = 200;
        boolean last = false;
        while (!last) {
            PageResponse<UserDto> users = userServiceClient.getUsers(page, size, null);
            List<UserDto> content = users.getContent();
            if (content != null) {
                for (UserDto u : content) {
                    if (!ObjectUtils.isEmpty(u.getBankToken())) {
                        consumer.accept(Objects.toString(u.getId()));
                    }
                }
            }
            last = users.isLast();
            page++;
        }
    }

    private String getDecryptedToken(String userId) {
        try {
            UserDto user = userServiceClient.getById(Long.valueOf(userId));
            if (user == null || ObjectUtils.isEmpty(user.getBankToken())) {
                return null;
            }
            DecryptRequest req = DecryptRequest.builder()
                    .userId(userId)
                    .encryptedData(user.getBankToken())
                    .build();
            DecryptResponse resp = keyManagementServiceClient.decrypt(req);
            if (resp != null && resp.isSuccess()) {
                return resp.getDecryptedData();
            }
            log.warn("Decrypt failed for user {}: {}", userId, resp != null ? resp.getMessage() : "null response");
            return null;
        } catch (Exception e) {
            log.error("Error decrypting token for user {}", userId, e);
            return null;
        }
    }

    private void upsertAccount(BankAccountDto dto, String userId) {
        Account existing = accountRepository.findByBankAccountIdAndUserId(dto.getId(), userId);
        if (existing == null) {
            existing = new Account();
        }
        existing.setBankAccountId(dto.getId());
        // Encrypt and hash PII fields in parallel
        Executor accountExec = Executors.newFixedThreadPool(6);
        var holderFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getAccountHolderName()), accountExec);
        var accountFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getAccountNumber()), accountExec);
        var shortFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getBankShortName()), accountExec);
        var fullFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getBankFullName()), accountExec);
        var binFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getBankBin()), accountExec);
        var codeFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, dto.getBankCode()), accountExec);
        CompletableFuture.allOf(holderFuture, accountFuture, shortFuture, fullFuture, binFuture, codeFuture).join();
        String encHolder = holderFuture.join();
        String encAccount = accountFuture.join();
        String encShort = shortFuture.join();
        String encFull = fullFuture.join();
        String encBin = binFuture.join();
        String encCode = codeFuture.join();

        existing.setAccountHolderName(encHolder != null ? encHolder : dto.getAccountHolderName());
        existing.setAccountHolderNameHash(HashUtils.sha256(dto.getAccountHolderName()));
        existing.setAccountNumber(encAccount != null ? encAccount : dto.getAccountNumber());
        existing.setAccountNumberHash(HashUtils.sha256(dto.getAccountNumber()));
        if(!dto.getAccumulated().equals("0.00")){
            existing.setAccumulated(dto.getAccumulated());
        }
        existing.setLabel(dto.getLabel());
        existing.setActive(dto.getActive());
        existing.setBankShortName(encShort != null ? encShort : dto.getBankShortName());
        existing.setBankShortNameHash(HashUtils.sha256(dto.getBankShortName()));
        existing.setBankFullName(encFull != null ? encFull : dto.getBankFullName());
        existing.setBankFullNameHash(HashUtils.sha256(dto.getBankFullName()));
        existing.setBankBin(encBin != null ? encBin : dto.getBankBin());
        existing.setBankBinHash(HashUtils.sha256(dto.getBankBin()));
        existing.setBankCode(encCode != null ? encCode : dto.getBankCode());
        existing.setBankCodeHash(HashUtils.sha256(dto.getBankCode()));
        existing.setUserId(userId);
        accountRepository.save(existing);
    }

    private Transaction mapTransaction(TransactionDto dto, String userId) {
        Transaction t = new Transaction();
        t.setId(dto.getId());
        // Encrypt and hash sensitive fields in parallel
        String plainBrand = dto.getBankBrandName();
        String plainAccountNumber = dto.getAccountNumber();
        Executor txnExec = Executors.newFixedThreadPool(4);
        var brandFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, plainBrand), txnExec);
        var encAccFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, plainAccountNumber), txnExec);
        CompletableFuture.allOf(brandFuture, encAccFuture).join();
        String encBrand = brandFuture.join();
        String encAccount = encAccFuture.join();
        t.setBankBrandName(encBrand != null ? encBrand : plainBrand);
        t.setBankBrandNameHash(HashUtils.sha256(plainBrand));
        t.setAccountNumber(encAccount != null ? encAccount : plainAccountNumber);
        t.setAccountNumberHash(HashUtils.sha256(plainAccountNumber));
        if (!ObjectUtils.isEmpty(dto.getTransactionDate())) {
            t.setTransactionDate(LocalDateTime.parse(dto.getTransactionDate(), DATE_TIME_FORMATTER));
        }
        t.setAmountOut(parseDouble(dto.getAmountOut()));
        t.setAmountIn(parseDouble(dto.getAmountIn()));
        t.setAccumulated(parseDouble(dto.getAccumulated()));
        t.setTransactionContent(dto.getTransactionContent());
        t.setReferenceNumber(dto.getReferenceNumber());
        t.setCode(dto.getCode());
        t.setSubAccount(dto.getSubAccount());
        t.setBankAccountId(dto.getBankAccountId());
        t.setUserId(userId);
        
        // Determine transaction type based on amounts
        Double amountIn = t.getAmountIn() != null ? t.getAmountIn() : 0.0;
        Double amountOut = t.getAmountOut() != null ? t.getAmountOut() : 0.0;
        if (amountIn > 0 && amountOut == 0) {
            t.setTransactionType("income");
        } else if (amountOut > 0 && amountIn == 0) {
            t.setTransactionType("expense");
        } else {
            // If both > 0 or both == 0, determine based on which is larger
            if (amountIn > amountOut) {
                t.setTransactionType("income");
            } else if (amountOut > amountIn) {
                t.setTransactionType("expense");
            } else {
                t.setTransactionType("unknown");
            }
        }
        
        // Set default category to "không xác định" when syncing new transactions
        t.setCategory("không xác định");
        
        return t;
    }

    private Double parseDouble(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Update account accumulated balance by applying a transaction's delta.
     * New accumulated = current accumulated + amountIn - amountOut
     */
    private void updateAccountAccumulated(String bankAccountId, String userId, Double amountIn, Double amountOut) {
        if (bankAccountId == null || bankAccountId.isEmpty()) {
            return;
        }

        try {
            Account account = accountRepository.findByBankAccountIdAndUserId(bankAccountId, userId);
            if (account != null) {
                double current = parseDouble(account.getAccumulated());
                double inVal = amountIn != null ? amountIn : 0.0;
                double outVal = amountOut != null ? amountOut : 0.0;
                double updated = current + inVal - outVal;
                String accumulatedStr = String.format("%.2f", updated);
                account.setAccumulated(accumulatedStr);
                accountRepository.save(account);
                log.debug("Updated accumulated for account {}: {} (current: {}, +in: {}, -out: {})",
                        bankAccountId, accumulatedStr, current, inVal, outVal);
            } else {
                log.warn("Account not found for bankAccountId: {} and userId: {}", bankAccountId, userId);
            }
        } catch (Exception e) {
            log.error("Error updating accumulated for account {}: {}", bankAccountId, e.getMessage(), e);
        }
    }

    private String encrypt(String userId, String plain) {
        if (ObjectUtils.isEmpty(plain)) {
            return plain;
        }
        try {
            EncryptRequest request = EncryptRequest.builder()
                    .userId(userId)
                    .data(plain)
                    .build();
            EncryptResponse response = keyManagementServiceClient.encrypt(request);
            if (response != null && response.isSuccess()) {
                return response.getEncryptedData();
            }
        } catch (Exception e) {
            log.warn("Encrypt failed for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    // ==========================================
    // OAuth2 Sync Methods
    // ==========================================

    /**
     * Sync accounts for a user using OAuth2 API (new endpoint format).
     * Called after OAuth2 connection is established.
     */
    public void syncAccountsForUserOAuth2(String userId) {
        String accessToken = oauth2TokenService.getValidAccessToken(userId);
        if (accessToken == null) {
            log.warn("No valid OAuth2 access token for user {} - skipping accounts sync", userId);
            return;
        }
        try {
            List<OAuth2BankAccountDto> accounts = webhookService.fetchBankAccountsOAuth2(accessToken);
            if (accounts == null || accounts.isEmpty()) {
                log.warn("No accounts returned (OAuth2) for user {}", userId);
                return;
            }
            for (OAuth2BankAccountDto dto : accounts) {
                upsertAccountFromOAuth2(dto, userId);
            }
            log.info("Accounts synced (OAuth2) for user {}: {} accounts", userId, accounts.size());
        } catch (Exception e) {
            log.error("Error syncing accounts (OAuth2) for user {}", userId, e);
        }
    }

    /**
     * API variant: sync accounts for a user using OAuth2 API, returns SyncResult.
     */
    public SyncResult syncAccountsForUserOAuth2Api(String userId) {
        String accessToken = oauth2TokenService.getValidAccessToken(userId);
        if (accessToken == null) {
            return SyncResult.builder().success(false).message("No valid OAuth2 access token").processedCount(0).build();
        }
        try {
            List<OAuth2BankAccountDto> accounts = webhookService.fetchBankAccountsOAuth2(accessToken);
            if (accounts == null || accounts.isEmpty()) {
                return SyncResult.builder().success(true).message("No accounts returned").processedCount(0).build();
            }
            for (OAuth2BankAccountDto dto : accounts) {
                upsertAccountFromOAuth2(dto, userId);
            }
            return SyncResult.builder().success(true).message("OK").processedCount(accounts.size()).build();
        } catch (Exception e) {
            return SyncResult.builder().success(false).message(e.getMessage()).processedCount(0).build();
        }
    }

    /**
     * Sync transactions for a user using OAuth2 API (new endpoint format).
     */
    public SyncResult syncTransactionsForUserOAuth2Api(String userId) {
        String accessToken = oauth2TokenService.getValidAccessToken(userId);
        if (accessToken == null) {
            return SyncResult.builder().success(false).message("No valid OAuth2 access token").processedCount(0).build();
        }
        try {
            List<OAuth2TransactionDto> transactions = webhookService.fetchTransactionsOAuth2(accessToken, null);
            if (transactions == null || transactions.isEmpty()) {
                return SyncResult.builder().success(true).message("No transactions returned").processedCount(0).build();
            }
            int saved = 0;
            for (OAuth2TransactionDto dto : transactions) {
                String txnId = String.valueOf(dto.getId());
                if (!transactionRepository.existsById(txnId)) {
                    Transaction t = mapOAuth2Transaction(dto, userId);
                    transactionRepository.save(t);
                    updateAccountAccumulated(t.getBankAccountId(), userId, t.getAmountIn(), t.getAmountOut());
                    saved++;
                }
            }
            return SyncResult.builder().success(true).message("OK").processedCount(saved).build();
        } catch (Exception e) {
            return SyncResult.builder().success(false).message(e.getMessage()).processedCount(0).build();
        }
    }

    /**
     * Sync all accounts for OAuth2-connected users (scheduled job).
     */
    public void syncAllAccountsOAuth2() {
        log.info("Starting syncAllAccountsOAuth2 job");
        List<String> connectedUserIds = oauth2TokenService.getConnectedUserIds();
        for (String userId : connectedUserIds) {
            try {
                syncAccountsForUserOAuth2(userId);
            } catch (Exception e) {
                log.error("Error syncing accounts (OAuth2) for user {}", userId, e);
            }
        }
    }

    /**
     * Upsert an account from OAuth2 API response format.
     * The OAuth2 format has nested bank info vs flat fields in legacy.
     */
    private void upsertAccountFromOAuth2(OAuth2BankAccountDto dto, String userId) {
        Account existing = accountRepository.findByBankAccountIdAndUserId(String.valueOf(dto.getId()), userId);
        if (existing == null) {
            existing = new Account();
        }
        existing.setBankAccountId(String.valueOf(dto.getId()));

        String holderName = dto.getAccountHolderName();
        String accountNumber = dto.getAccountNumber();
        String shortName = dto.getBank() != null ? dto.getBank().getShortName() : null;
        String fullName = dto.getBank() != null ? dto.getBank().getFullName() : null;
        String bin = dto.getBank() != null ? dto.getBank().getBin() : null;
        String code = dto.getBank() != null ? dto.getBank().getCode() : null;

        Executor exec = Executors.newFixedThreadPool(6);
        var holderFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, holderName), exec);
        var accountFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, accountNumber), exec);
        var shortFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, shortName), exec);
        var fullFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, fullName), exec);
        var binFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, bin), exec);
        var codeFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, code), exec);
        CompletableFuture.allOf(holderFuture, accountFuture, shortFuture, fullFuture, binFuture, codeFuture).join();

        existing.setAccountHolderName(holderFuture.join() != null ? holderFuture.join() : holderName);
        existing.setAccountHolderNameHash(HashUtils.sha256(holderName));
        existing.setAccountNumber(accountFuture.join() != null ? accountFuture.join() : accountNumber);
        existing.setAccountNumberHash(HashUtils.sha256(accountNumber));
        if (dto.getAccumulated() != null && dto.getAccumulated() != 0.0) {
            existing.setAccumulated(String.format("%.2f", dto.getAccumulated()));
        }
        existing.setLabel(dto.getLabel());
        existing.setActive(dto.getActive() != null && dto.getActive() ? "true" : "false");
        existing.setBankShortName(shortFuture.join() != null ? shortFuture.join() : shortName);
        existing.setBankShortNameHash(HashUtils.sha256(shortName));
        existing.setBankFullName(fullFuture.join() != null ? fullFuture.join() : fullName);
        existing.setBankFullNameHash(HashUtils.sha256(fullName));
        existing.setBankBin(binFuture.join() != null ? binFuture.join() : bin);
        existing.setBankBinHash(HashUtils.sha256(bin));
        existing.setBankCode(codeFuture.join() != null ? codeFuture.join() : code);
        existing.setBankCodeHash(HashUtils.sha256(code));
        existing.setUserId(userId);
        accountRepository.save(existing);
    }

    /**
     * Map an OAuth2 transaction DTO to a Transaction document.
     */
    private Transaction mapOAuth2Transaction(OAuth2TransactionDto dto, String userId) {
        Transaction t = new Transaction();
        t.setId(String.valueOf(dto.getId()));

        String plainBrand = dto.getBankBrandName();
        String plainAccountNumber = dto.getAccountNumber();
        Executor exec = Executors.newFixedThreadPool(2);
        var brandFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, plainBrand), exec);
        var accFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, plainAccountNumber), exec);
        CompletableFuture.allOf(brandFuture, accFuture).join();

        t.setBankBrandName(brandFuture.join() != null ? brandFuture.join() : plainBrand);
        t.setBankBrandNameHash(HashUtils.sha256(plainBrand));
        t.setAccountNumber(accFuture.join() != null ? accFuture.join() : plainAccountNumber);
        t.setAccountNumberHash(HashUtils.sha256(plainAccountNumber));

        if (!ObjectUtils.isEmpty(dto.getTransactionDate())) {
            t.setTransactionDate(LocalDateTime.parse(dto.getTransactionDate(), DATE_TIME_FORMATTER));
        }
        t.setAmountOut(dto.getAmountOut() != null ? dto.getAmountOut() : 0.0);
        t.setAmountIn(dto.getAmountIn() != null ? dto.getAmountIn() : 0.0);
        t.setAccumulated(dto.getAccumulated() != null ? dto.getAccumulated() : 0.0);
        t.setTransactionContent(dto.getTransactionContent());
        t.setReferenceNumber(dto.getReferenceNumber());
        t.setCode(dto.getCode());
        t.setSubAccount(dto.getSubAccount());
        t.setBankAccountId(dto.getBankAccountId() != null ? String.valueOf(dto.getBankAccountId()) : null);
        t.setUserId(userId);

        // Determine transaction type
        double amountIn = t.getAmountIn() != null ? t.getAmountIn() : 0.0;
        double amountOut = t.getAmountOut() != null ? t.getAmountOut() : 0.0;
        if (amountIn > 0 && amountOut == 0) {
            t.setTransactionType("income");
        } else if (amountOut > 0 && amountIn == 0) {
            t.setTransactionType("expense");
        } else if (amountIn > amountOut) {
            t.setTransactionType("income");
        } else if (amountOut > amountIn) {
            t.setTransactionType("expense");
        } else {
            t.setTransactionType("unknown");
        }

        t.setCategory("không xác định");
        return t;
    }
}


