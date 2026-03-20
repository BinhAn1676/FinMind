package com.finance.financeservice.mongo.document;

import lombok.*;
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
@Document(collection = "bill_reminders")
@CompoundIndex(name = "user_bill_idx", def = "{ 'user_id': 1 }")
public class BillReminder {

    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("name")
    private String name;

    @Field("amount")
    private Double amount;

    @Field("cycle")
    private BillCycle cycle;

    @Field("day_of_month")
    private Integer dayOfMonth;

    @Field("icon")
    private String icon;

    @Field("color")
    private String color;

    @Field("notes")
    private String notes;

    @Field("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Field("remind_days_before")
    @Builder.Default
    private Integer remindDaysBefore = 3;

    @Field("payments")
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum BillCycle {
        MONTHLY, QUARTERLY, YEARLY
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payment {
        private String id;

        @Field("period")
        private String period; // format: YYYY-MM

        @Field("paid_at")
        private LocalDate paidAt;

        @Field("note")
        private String note;
    }
}
