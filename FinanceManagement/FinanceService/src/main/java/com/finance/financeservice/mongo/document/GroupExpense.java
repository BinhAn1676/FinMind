package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "group_expenses")
public class GroupExpense {

    @Id
    private String id;

    @Field("group_id")
    @Indexed
    private Long groupId;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("amount")
    private Double amount;

    @Field("paid_by_user_id")
    private Long paidByUserId;

    @Field("paid_by_name")
    private String paidByName;

    @Field("split_type")
    private SplitType splitType;

    @Field("shares")
    @Builder.Default
    private List<SplitShare> shares = new ArrayList<>();

    @Field("category")
    private String category;

    @Field("date")
    private LocalDate date;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    public enum SplitType {
        EQUAL, CUSTOM
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitShare {

        @Field("user_id")
        private Long userId;

        @Field("user_name")
        private String userName;

        @Field("amount")
        private Double amount;

        @Field("settled")
        @Builder.Default
        private Boolean settled = false;

        @Field("settled_at")
        private LocalDateTime settledAt;
    }
}
