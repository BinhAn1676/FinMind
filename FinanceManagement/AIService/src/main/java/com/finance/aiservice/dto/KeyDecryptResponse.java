package com.finance.aiservice.dto;

import lombok.Builder;

/**
 * Response DTO from KeyManagementService decrypt endpoint.
 */
@Builder
public record KeyDecryptResponse(
    boolean success,
    String decryptedData,
    String message
) {}
