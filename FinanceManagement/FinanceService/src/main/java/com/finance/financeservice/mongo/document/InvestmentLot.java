package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "investment_lots")
@CompoundIndex(name = "userId_idx", def = "{'user_id': 1}")
public class InvestmentLot {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("asset_type")
    private AssetType assetType;

    private String symbol;

    private String name;

    @Field("buy_date")
    private LocalDate buyDate;

    private Double quantity;

    @Field("buy_price_vnd")
    private Double buyPriceVnd;

    private String note;

    @Field("transaction_type")
    @Builder.Default
    private TransactionType transactionType = TransactionType.BUY;

    private Double fees;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum AssetType {
        CRYPTO, GOLD, VN_STOCK
    }

    public enum TransactionType {
        BUY, SELL
    }
}
