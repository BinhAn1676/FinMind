package com.finance.financeservice.service.sepay;

import com.finance.financeservice.config.SepayApiProperties;
import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.dto.keys.EncryptRequest;
import com.finance.financeservice.dto.keys.EncryptResponse;
import com.finance.financeservice.dto.sepay.oauth2.*;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.TransactionRepository;
import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.mysql.repository.AccountRepository;
import com.finance.financeservice.service.PlanningBudgetService;
import com.finance.financeservice.service.client.KeyManagementServiceClient;
import com.finance.financeservice.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages SePay webhooks via OAuth2 API and processes incoming webhook events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SepayWebhookService {

    private final SepayOAuth2Properties oauth2Properties;
    private final SepayApiProperties apiProperties;
    private final SepayOAuth2TokenService tokenService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PlanningBudgetService planningBudgetService;
    private final KeyManagementServiceClient keyManagementServiceClient;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Create webhooks for all bank accounts of a user.
     * Called after OAuth2 connection is established and accounts are synced.
     */
    public void createWebhooksForUser(String userId) {
        if (!oauth2Properties.isEnabled()) {
            log.debug("OAuth2 disabled, skipping webhook creation for userId: {}", userId);
            return;
        }

        String accessToken = tokenService.getValidAccessToken(userId);
        if (accessToken == null) {
            log.warn("No valid access token for userId: {}, cannot create webhooks", userId);
            return;
        }

        // Get all bank accounts for the user from SePay
        List<OAuth2BankAccountDto> accounts = fetchBankAccountsOAuth2(accessToken);
        if (accounts == null || accounts.isEmpty()) {
            log.warn("No bank accounts found for userId: {}", userId);
            return;
        }

        // Get existing webhooks
        List<OAuth2WebhookDto> existingWebhooks = fetchWebhooksOAuth2(accessToken);

        for (OAuth2BankAccountDto account : accounts) {
            // Check if webhook already exists for this bank account
            boolean exists = existingWebhooks != null && existingWebhooks.stream()
                    .anyMatch(w -> w.getBankAccountId().equals(account.getId())
                            && oauth2Properties.getWebhookCallbackUrl().equals(w.getWebhookUrl()));

            if (exists) {
                log.info("Webhook already exists for bank account {} of userId: {}", account.getId(), userId);
                continue;
            }

            createWebhookForAccount(accessToken, account, userId);
        }
    }

    /**
     * Create a webhook for a specific bank account.
     */
    private void createWebhookForAccount(String accessToken, OAuth2BankAccountDto account, String userId) {
        CreateWebhookRequest request = CreateWebhookRequest.builder()
                .bankAccountId(account.getId())
                .name("Finance App - " + (account.getLabel() != null ? account.getLabel() : account.getAccountNumber()))
                .eventType(oauth2Properties.getWebhookEventType())
                .authenType(oauth2Properties.getWebhookAuthType())
                .webhookUrl(oauth2Properties.getWebhookCallbackUrl())
                .isVerifyPayment(0)
                .skipIfNoCode(0)
                .active(1)
                .requestContentType("Json")
                .build();

        // Add API key if auth type is Api_Key
        if ("Api_Key".equals(oauth2Properties.getWebhookAuthType())
                && !ObjectUtils.isEmpty(oauth2Properties.getWebhookApiKey())) {
            request.setApiKey(oauth2Properties.getWebhookApiKey());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<CreateWebhookRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<CreateWebhookResponse> response = restTemplate.exchange(
                    apiProperties.getOauth2WebhooksUrl(), HttpMethod.POST, entity, CreateWebhookResponse.class);

            CreateWebhookResponse body = response.getBody();
            if (body != null) {
                log.info("Webhook created for bank account {} of userId: {}, webhookId: {}",
                        account.getId(), userId, body.getId());
            }
        } catch (Exception e) {
            log.error("Error creating webhook for bank account {} of userId: {}",
                    account.getId(), userId, e);
        }
    }

    /**
     * Process an incoming webhook event from SePay (real-time transaction).
     */
    public void processWebhookEvent(SepayWebhookEvent event) {
        log.info("Processing SePay webhook event: id={}, accountNumber={}, transferType={}",
                event.getId(), event.getAccountNumber(), event.getTransferType());

        if (event.getId() == null || event.getAccountNumber() == null) {
            log.warn("Invalid webhook event: missing id or accountNumber");
            return;
        }

        // Find the account by account number hash
        String accountNumberHash = HashUtils.sha256(event.getAccountNumber());
        List<Account> accounts = accountRepository.findByAccountNumberHash(accountNumberHash);
        if (accounts.isEmpty()) {
            log.warn("No account found for accountNumber hash from webhook event");
            return;
        }

        for (Account account : accounts) {
            String userId = account.getUserId();
            String transactionId = String.valueOf(event.getId());

            // Check if transaction already exists
            if (transactionRepository.existsById(transactionId)) {
                log.info("Transaction {} already exists, skipping", transactionId);
                continue;
            }

            // Map webhook event to Transaction
            Transaction t = mapWebhookEventToTransaction(event, account, userId);
            transactionRepository.save(t);

            // Update account accumulated balance
            if (event.getAccumulated() != null && event.getAccumulated() != 0.0) {
                account.setAccumulated(String.format("%.2f", event.getAccumulated()));
                accountRepository.save(account);
            }

            // Reflect expense into planning budgets
            if (t.getAmountOut() != null && t.getAmountOut() > 0
                    && t.getTransactionDate() != null
                    && t.getCategory() != null
                    && !"không xác định".equalsIgnoreCase(t.getCategory())) {
                planningBudgetService.addExpense(userId, t.getCategory(), t.getAmountOut(), t.getTransactionDate().toLocalDate());
            }

            log.info("Webhook transaction {} processed for userId: {}", transactionId, userId);
        }
    }

    /**
     * Fetch bank accounts using OAuth2 API.
     */
    public List<OAuth2BankAccountDto> fetchBankAccountsOAuth2(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<OAuth2BankAccountListResponse> response = restTemplate.exchange(
                    apiProperties.getOauth2AccountsUrl(),
                    HttpMethod.GET,
                    entity,
                    OAuth2BankAccountListResponse.class);

            OAuth2BankAccountListResponse body = response.getBody();
            if (body != null && "success".equals(body.getStatus())) {
                return body.getData();
            }
        } catch (Exception e) {
            log.error("Error fetching bank accounts via OAuth2", e);
        }
        return null;
    }

    /**
     * Fetch transactions using OAuth2 API.
     */
    public List<OAuth2TransactionDto> fetchTransactionsOAuth2(String accessToken, Integer bankAccountId) {
        String url = apiProperties.getOauth2TransactionsUrl();
        if (bankAccountId != null) {
            url += "?bank_account_id=" + bankAccountId;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<OAuth2TransactionListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    OAuth2TransactionListResponse.class);

            OAuth2TransactionListResponse body = response.getBody();
            if (body != null && "success".equals(body.getStatus())) {
                return body.getData();
            }
        } catch (Exception e) {
            log.error("Error fetching transactions via OAuth2", e);
        }
        return null;
    }

    /**
     * Fetch existing webhooks using OAuth2 API.
     */
    private List<OAuth2WebhookDto> fetchWebhooksOAuth2(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<OAuth2WebhookListResponse> response = restTemplate.exchange(
                    apiProperties.getOauth2WebhooksUrl(),
                    HttpMethod.GET,
                    entity,
                    OAuth2WebhookListResponse.class);

            OAuth2WebhookListResponse body = response.getBody();
            if (body != null && "success".equals(body.getStatus())) {
                return body.getData();
            }
        } catch (Exception e) {
            log.error("Error fetching webhooks via OAuth2", e);
        }
        return null;
    }

    /**
     * Delete all webhooks for a user (called when disconnecting).
     */
    public void deleteWebhooksForUser(String userId) {
        String accessToken = tokenService.getValidAccessToken(userId);
        if (accessToken == null) {
            log.warn("No valid access token for userId: {}, cannot delete webhooks", userId);
            return;
        }

        List<OAuth2WebhookDto> webhooks = fetchWebhooksOAuth2(accessToken);
        if (webhooks == null || webhooks.isEmpty()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        for (OAuth2WebhookDto webhook : webhooks) {
            if (oauth2Properties.getWebhookCallbackUrl().equals(webhook.getWebhookUrl())) {
                try {
                    restTemplate.exchange(
                            apiProperties.getOauth2WebhooksUrl() + "/" + webhook.getId(),
                            HttpMethod.DELETE,
                            new HttpEntity<>(headers),
                            String.class);
                    log.info("Deleted webhook {} for userId: {}", webhook.getId(), userId);
                } catch (Exception e) {
                    log.error("Error deleting webhook {} for userId: {}", webhook.getId(), userId, e);
                }
            }
        }
    }

    private Transaction mapWebhookEventToTransaction(SepayWebhookEvent event, Account account, String userId) {
        Transaction t = new Transaction();
        t.setId(String.valueOf(event.getId()));

        // Encrypt PII fields
        Executor exec = Executors.newFixedThreadPool(2);
        var brandFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, event.getGateway()), exec);
        var accFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, event.getAccountNumber()), exec);
        CompletableFuture.allOf(brandFuture, accFuture).join();

        String encBrand = brandFuture.join();
        String encAcc = accFuture.join();

        t.setBankBrandName(encBrand != null ? encBrand : event.getGateway());
        t.setBankBrandNameHash(HashUtils.sha256(event.getGateway()));
        t.setAccountNumber(encAcc != null ? encAcc : event.getAccountNumber());
        t.setAccountNumberHash(HashUtils.sha256(event.getAccountNumber()));

        if (!ObjectUtils.isEmpty(event.getTransactionDate())) {
            try {
                t.setTransactionDate(LocalDateTime.parse(event.getTransactionDate(), DATE_TIME_FORMATTER));
            } catch (Exception e) {
                t.setTransactionDate(LocalDateTime.now());
            }
        } else {
            t.setTransactionDate(LocalDateTime.now());
        }

        // Determine amounts based on transfer type
        if ("in".equalsIgnoreCase(event.getTransferType())) {
            t.setAmountIn(event.getTransferAmount() != null ? event.getTransferAmount() : 0.0);
            t.setAmountOut(0.0);
            t.setTransactionType("income");
        } else {
            t.setAmountOut(event.getTransferAmount() != null ? event.getTransferAmount() : 0.0);
            t.setAmountIn(0.0);
            t.setTransactionType("expense");
        }

        t.setAccumulated(event.getAccumulated() != null ? event.getAccumulated() : 0.0);
        t.setTransactionContent(event.getContent());
        t.setReferenceNumber(event.getReferenceCode());
        t.setCode(event.getCode());
        t.setSubAccount(event.getSubAccount());
        t.setBankAccountId(account.getBankAccountId());
        t.setUserId(userId);
        t.setCategory("không xác định");

        return t;
    }

    private String encrypt(String userId, String plain) {
        if (ObjectUtils.isEmpty(plain)) return plain;
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
            log.warn("Encrypt failed for userId {}: {}", userId, e.getMessage());
        }
        return null;
    }

    // --- Inner response types for deserialization ---

    @lombok.Data
    static class OAuth2BankAccountListResponse {
        private String status;
        private List<OAuth2BankAccountDto> data;
    }

    @lombok.Data
    static class OAuth2TransactionListResponse {
        private String status;
        private List<OAuth2TransactionDto> data;
    }

    @lombok.Data
    static class OAuth2WebhookListResponse {
        private String status;
        private List<OAuth2WebhookDto> data;
    }
}
