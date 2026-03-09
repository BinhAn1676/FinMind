package com.finance.aiservice.client;

import com.finance.aiservice.dto.KeyDecryptRequest;
import com.finance.aiservice.dto.KeyDecryptResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for KeyManagementService integration.
 *
 * Used by DataSanitizer to decrypt encrypted transaction data from FinanceService.
 * Uses Eureka service discovery - no hardcoded URL.
 */
@FeignClient(name = "keys")
public interface KeyManagementServiceClient {

    /**
     * Decrypt encrypted data using KeyManagementService.
     *
     * @param request Decrypt request with userId and encryptedData
     * @return Decrypt response with decryptedData
     */
    @PostMapping("/api/v1/keys/decrypt")
    KeyDecryptResponse decrypt(@RequestBody KeyDecryptRequest request);
}
