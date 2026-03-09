package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bank account from OAuth2 API: GET /api/v1/bank-accounts
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2BankAccountDto {
    private Integer id;
    private String label;

    @JsonProperty("account_holder_name")
    private String accountHolderName;

    @JsonProperty("account_number")
    private String accountNumber;

    private Double accumulated;
    private Boolean active;

    @JsonProperty("created_at")
    private String createdAt;

    private OAuth2BankInfo bank;
}
