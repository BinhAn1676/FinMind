package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolio_coins")
@CompoundIndex(name = "userId_coinId_idx", def = "{'user_id': 1, 'coin_id': 1}")
public class PortfolioCoin {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("coin_id")
    private String coinId;

    private String symbol;

    private String name;

    @Field("added_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
