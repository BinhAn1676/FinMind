package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook data from SePay OAuth2 API: GET /api/v1/webhooks
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2WebhookDto {
    private Integer id;

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
    private Boolean isVerifyPayment;

    @JsonProperty("skip_if_no_code")
    private Boolean skipIfNoCode;

    @JsonProperty("only_va")
    private Boolean onlyVa;

    private Boolean active;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("api_key")
    private String apiKey;

    @JsonProperty("request_content_type")
    private String requestContentType;
}
