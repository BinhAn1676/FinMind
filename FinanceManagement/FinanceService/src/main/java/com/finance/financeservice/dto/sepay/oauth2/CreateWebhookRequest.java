package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body to create a webhook via SePay OAuth2 API.
 * POST /api/v1/webhooks
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateWebhookRequest {
    @JsonProperty("bank_account_id")
    private Integer bankAccountId;

    private String name;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("authen_type")
    private String authenType;

    @JsonProperty("webhook_url")
    private String webhookUrl;

    @JsonProperty("is_verify_payment")
    private Integer isVerifyPayment;

    @JsonProperty("skip_if_no_code")
    private Integer skipIfNoCode;

    private Integer active;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("request_content_type")
    private String requestContentType;
}
