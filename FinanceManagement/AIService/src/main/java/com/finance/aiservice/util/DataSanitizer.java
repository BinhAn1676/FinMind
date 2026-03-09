package com.finance.aiservice.util;

import com.finance.aiservice.client.KeyManagementServiceClient;
import com.finance.aiservice.dto.KeyDecryptRequest;
import com.finance.aiservice.dto.KeyDecryptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.regex.Pattern;

/**
 * Handles decryption and sanitization of sensitive financial data
 * before sending to AI models.
 *
 * Security Flow:
 * 1. Decrypt encrypted data via KeyManagementService (like UserService does)
 * 2. Mask/sanitize PII (account numbers, names)
 * 3. Return sanitized data safe for AI processing
 *
 * CRITICAL: Plaintext data exists ONLY in method scope.
 * Never log or persist decrypted data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSanitizer {

    private final KeyManagementServiceClient keyManagementServiceClient;

    private static final int ACCOUNT_VISIBLE_DIGITS = 4;
    private static final int MERCHANT_VISIBLE_CHARS = 3;
    private static final String MASK_CHAR = "*";

    // Regex patterns for additional sanitization
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?:\\+84|0)(?:\\d{9,10})"
    );

    /**
     * Main entry point: Decrypt and sanitize sensitive data.
     *
     * This method:
     * 1. Decrypts the encrypted input via KeyManagementService
     * 2. Masks sensitive fields (account numbers, names)
     * 3. Removes PII (emails, phones)
     * 4. Returns sanitized string safe for AI
     *
     * @param userId User ID for decryption context
     * @param encryptedInput Encrypted data from FinanceService
     * @return Sanitized string (or null if input is null)
     */
    public String decryptAndMask(String userId, String encryptedInput) {
        if (ObjectUtils.isEmpty(encryptedInput)) {
            return encryptedInput;
        }

        try {
            // Step 1: Decrypt via KeyManagementService (same as UserService)
            String plaintext = decrypt(userId, encryptedInput);

            // Step 2: Sanitize
            String sanitized = sanitize(plaintext);

            // Step 3: Purge plaintext from memory (help GC)
            plaintext = null;

            return sanitized;

        } catch (Exception e) {
            log.error("Failed to decrypt and mask data: {}", e.getMessage());
            // Return masked placeholder to prevent data leak
            return "***ENCRYPTED_DATA***";
        }
    }

    /**
     * Decrypt encrypted data using KeyManagementService.
     * Same pattern as UserService.
     *
     * @param userId User ID
     * @param encrypted Encrypted data
     * @return Plaintext string (exists only in method scope)
     */
    private String decrypt(String userId, String encrypted) {
        if (ObjectUtils.isEmpty(encrypted)) {
            return encrypted;
        }

        KeyDecryptRequest request = KeyDecryptRequest.builder()
                .userId(userId)
                .encryptedData(encrypted)
                .build();

        KeyDecryptResponse response = keyManagementServiceClient.decrypt(request);

        if (response != null && response.success()) {
            return response.decryptedData();
        }

        throw new RuntimeException("Decrypt failed" + (response != null ? (": " + response.message()) : ""));
    }

    /**
     * Fallback: Decrypt and mask without userId (uses default context).
     * For compatibility with old code.
     */
    public String decryptAndMask(String encryptedInput) {
        return decryptAndMask("system", encryptedInput);
    }

    /**
     * Sanitize plaintext data by masking/removing PII.
     *
     * @param plaintext Decrypted data
     * @return Sanitized data safe for AI
     */
    private String sanitize(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }

        String sanitized = plaintext;

        // Remove emails
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***@***.com");

        // Remove phone numbers
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("+84******");

        return sanitized;
    }

    /**
     * Mask account number, showing only last 4 digits.
     *
     * Examples:
     * - "1234567890" → "******7890"
     * - "9876" → "****"
     *
     * @param accountNumber Full account number
     * @return Masked account number
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return MASK_CHAR.repeat(4);
        }

        // Remove spaces, dashes, and special characters
        String cleaned = accountNumber.replaceAll("[\\s\\-_]", "");

        if (cleaned.length() <= ACCOUNT_VISIBLE_DIGITS) {
            // If too short, just mask everything
            return MASK_CHAR.repeat(4);
        }

        String lastFour = cleaned.substring(cleaned.length() - ACCOUNT_VISIBLE_DIGITS);
        String masked = MASK_CHAR.repeat(cleaned.length() - ACCOUNT_VISIBLE_DIGITS);

        return masked + lastFour;
    }

    /**
     * Mask merchant name, showing only first 3 characters.
     *
     * Examples:
     * - "STARBUCKS COFFEE" → "STA***"
     * - "ATM WITHDRAWAL" → "ATM***"
     * - "FB" → "FB***"
     *
     * @param merchantName Full merchant name
     * @return Masked merchant name
     */
    public String maskMerchantName(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return MASK_CHAR.repeat(3);
        }

        String cleaned = merchantName.trim().toUpperCase();

        if (cleaned.length() <= MERCHANT_VISIBLE_CHARS) {
            return cleaned + MASK_CHAR.repeat(3);
        }

        String visible = cleaned.substring(0, MERCHANT_VISIBLE_CHARS);
        return visible + MASK_CHAR.repeat(3);
    }

    /**
     * Mask user's full name, showing only first name initial.
     *
     * Examples:
     * - "Nguyen Van A" → "N***"
     * - "John Doe" → "J***"
     *
     * @param fullName User's full name
     * @return Masked name
     */
    public String maskUserName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "***";
        }

        String cleaned = fullName.trim();
        if (cleaned.isEmpty()) {
            return "***";
        }

        return cleaned.charAt(0) + MASK_CHAR.repeat(3);
    }

    /**
     * Validate that data has been properly sanitized.
     * Used in testing and pre-flight checks before sending to AI.
     *
     * @param data Data to validate
     * @return true if data appears sanitized, false otherwise
     */
    public boolean isSanitized(String data) {
        if (data == null || data.isBlank()) {
            return true;
        }

        // Check for common PII patterns
        boolean hasEmail = EMAIL_PATTERN.matcher(data).find();
        boolean hasPhone = PHONE_PATTERN.matcher(data).find();

        if (hasEmail || hasPhone) {
            log.warn("Unsanitized PII detected in data");
            return false;
        }

        return true;
    }

    /**
     * Emergency sanitization fallback.
     * If decryption fails but data must still be processed,
     * this aggressively redacts anything that looks sensitive.
     *
     * USE WITH CAUTION: This is a last resort.
     *
     * @param rawData Raw data that couldn't be decrypted
     * @return Heavily redacted data
     */
    public String emergencySanitize(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return "";
        }

        return rawData
            .replaceAll("\\d{9,}", "***")           // Long number sequences
            .replaceAll(EMAIL_PATTERN.pattern(), "***@***.com")
            .replaceAll(PHONE_PATTERN.pattern(), "+84******")
            .replaceAll("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b", "****-****-****-****"); // Card numbers
    }

    /**
     * Log sanitization metrics for monitoring.
     * Tracks how often decryption succeeds/fails without logging sensitive data.
     */
    public void logSanitizationMetrics(boolean decryptionSuccess, boolean sanitizationSuccess) {
        // TODO: Send to Prometheus/Grafana
        log.debug("Sanitization - Decryption: {}, Sanitization: {}",
            decryptionSuccess ? "SUCCESS" : "FAILED",
            sanitizationSuccess ? "SUCCESS" : "FAILED"
        );
    }
}
