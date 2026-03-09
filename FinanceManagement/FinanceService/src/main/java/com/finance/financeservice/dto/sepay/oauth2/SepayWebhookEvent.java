package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook event payload sent by SePay when a transaction occurs.
 * SePay sends a POST request to the configured webhook URL with this data.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SepayWebhookEvent {
    private Integer id;
    private String gateway;

    @JsonProperty("transactionDate")
    private String transactionDate;

    @JsonProperty("accountNumber")
    private String accountNumber;

    @JsonProperty("subAccount")
    private String subAccount;

    private String code;
    private String content;

    @JsonProperty("transferType")
    private String transferType; // "in" or "out"

    @JsonProperty("transferAmount")
    private Double transferAmount;

    private Double accumulated;

    @JsonProperty("referenceCode")
    private String referenceCode;

    private String description;
}
