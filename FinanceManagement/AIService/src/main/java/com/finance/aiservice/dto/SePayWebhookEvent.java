package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SePay webhook event DTO.
 *
 * SePay sends this when a transaction occurs in real-time.
 */
@Builder
public record SePayWebhookEvent(

    /**
     * Event type from SePay.
     * Examples: "transaction.created", "transaction.updated", "transaction.failed"
     */
    @NotBlank
    @JsonProperty("event")
    String event,

    /**
     * Webhook timestamp from SePay.
     */
    @NotNull
    @JsonProperty("timestamp")
    LocalDateTime timestamp,

    /**
     * Transaction data payload.
     * Contains encrypted/sensitive fields that need sanitization.
     */
    @NotNull
    @JsonProperty("data")
    Map<String, Object> data

) {}
