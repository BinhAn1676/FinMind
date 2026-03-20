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
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "savings_goals")
@CompoundIndex(name = "user_savings_idx", def = "{ 'user_id': 1 }")
@CompoundIndex(name = "group_savings_idx", def = "{ 'group_id': 1 }")
public class SavingsGoal {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("group_id")
    private Long groupId;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @Field("target_amount")
    private Double targetAmount;

    @Field("current_amount")
    @Builder.Default
    private Double currentAmount = 0.0;

    @Field("target_date")
    private LocalDate targetDate;

    @Field("status")
    private GoalStatus status;

    @Field("icon")
    private String icon;

    @Field("color")
    private String color;

    @Field("contributions")
    @Builder.Default
    private List<Contribution> contributions = new ArrayList<>();

    @Field("auto_save_amount")
    private Double autoSaveAmount;

    @Field("auto_save_cycle")
    private AutoSaveCycle autoSaveCycle;

    @Field("auto_save_enabled")
    @Builder.Default
    private Boolean autoSaveEnabled = false;

    @Field("last_auto_save_at")
    private LocalDate lastAutoSaveAt;

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum GoalStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    public enum AutoSaveCycle {
        DAILY, WEEKLY, MONTHLY
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Contribution {
        private String id;

        @Field("amount")
        private Double amount;

        @Field("note")
        private String note;

        @Field("created_at")
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}
