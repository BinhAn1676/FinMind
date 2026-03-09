package com.finance.financeservice.dto.sepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankAccountDto {
    private String id;
    @JsonProperty("account_holder_name")
    private String accountHolderName;
    @JsonProperty("account_number")
    private String accountNumber;
    private String accumulated;
    @JsonProperty("last_transaction")
    private String lastTransaction;
    private String label;
    private String active;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("bank_short_name")
    private String bankShortName;
    @JsonProperty("bank_full_name")
    private String bankFullName;
    @JsonProperty("bank_bin")
    private String bankBin;
    @JsonProperty("bank_code")
    private String bankCode;
}


