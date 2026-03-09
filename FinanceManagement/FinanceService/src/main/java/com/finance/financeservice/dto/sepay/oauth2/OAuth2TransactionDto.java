package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction from OAuth2 API: GET /api/v1/transactions
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2TransactionDto {
    private Integer id;

    @JsonProperty("bank_account_id")
    private Integer bankAccountId;

    @JsonProperty("bank_brand_name")
    private String bankBrandName;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("transaction_date")
    private String transactionDate;

    @JsonProperty("amount_out")
    private Double amountOut;

    @JsonProperty("amount_in")
    private Double amountIn;

    private Double accumulated;

    @JsonProperty("transaction_content")
    private String transactionContent;

    @JsonProperty("reference_number")
    private String referenceNumber;

    private String code;

    @JsonProperty("sub_account")
    private String subAccount;
}
