package com.finance.aiservice.dto;

import lombok.Builder;

/**
 * Request DTO for KeyManagementService decrypt endpoint.
 */
@Builder
public record KeyDecryptRequest(
    String userId,
    String encryptedData
) {}
