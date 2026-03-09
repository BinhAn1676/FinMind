package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
public class Transaction {
    @Id
    private String id;

    @Field("bank_brand_name")
    private String bankBrandName;

    @Field("bank_brand_name_hash")
    private String bankBrandNameHash;

    @Field("account_number")
    private String accountNumber;

    @Field("account_number_hash")
    private String accountNumberHash;

    @Field("transaction_date")
    private LocalDateTime transactionDate;

    @Field("amount_out")
    private Double amountOut;

    @Field("amount_in")
    private Double amountIn;

    @Field("accumulated")
    private Double accumulated;

    @Field("transaction_content")
    private String transactionContent;

    @Field("reference_number")
    private String referenceNumber;

    @Field("code")
    private String code;

    @Field("sub_account")
    private String subAccount;

    @Field("bank_account_id")
    private String bankAccountId;

    @Field("user_id")
    private String userId;

    @Field("transaction_type")
    private String transactionType; // "income" or "expense"

    @Field("category")
    private String category; // Category/classification of transaction, default "không xác định"
}
