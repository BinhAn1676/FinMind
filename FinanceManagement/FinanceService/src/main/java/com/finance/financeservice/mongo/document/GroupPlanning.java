package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "group_plannings")
@CompoundIndex(name = "group_category_date_idx", def = "{ 'group_id': 1, 'category': 1, 'start_date': 1, 'end_date': 1 }")
public class GroupPlanning {
    @Id
    private String id;

    @Field("group_id")
    private Long groupId;

    @Field("category")
    private String category; // category name (re-uses transaction category)

    @Field("budget_amount")
    private Double budgetAmount; // target amount for the period

    @Field("spent_amount")
    private Double spentAmount; // computed from transactions mapped to the category within date range

    @Field("plan_type")
    private PlanType planType; // SHORT_TERM, LONG_TERM, RECURRING

    @Field("start_date")
    private LocalDate startDate; // required for SHORT_TERM and LONG_TERM, optional for RECURRING

    @Field("end_date")
    private LocalDate endDate; // required for SHORT_TERM, optional for LONG_TERM, not used for RECURRING

    @Field("repeat_cycle")
    private RepeatCycle repeatCycle; // MONTHLY, QUARTERLY, YEARLY - used for RECURRING and optionally LONG_TERM

    @Field("day_of_month")
    private Integer dayOfMonth; // 1-31, used for RECURRING plans (when to reset budget)

    @Field("icon")
    private String icon; // Optional emoji icon for the planning

    @Field("color")
    private String color; // Optional hex color for the planning

    @Field("description")
    private String description; // Optional description for the planning

    @Field("created_by")
    private String createdBy; // User ID who created this planning

    @Field("created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Field("updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public double remaining() {
        double budget = budgetAmount != null ? budgetAmount : 0.0;
        double spent = spentAmount != null ? spentAmount : 0.0;
        return budget - spent;
    }

    public enum PlanType {
        SHORT_TERM,   // Ngắn hạn: có startDate và endDate rõ ràng, thường < 3 tháng
        LONG_TERM,    // Dài hạn: có thể kéo dài nhiều tháng/năm, có startDate, endDate hoặc repeatCycle YEARLY
        RECURRING     // Định kỳ: lặp lại theo tháng/quý/năm, dùng repeatCycle và dayOfMonth
    }

    public enum RepeatCycle {
        MONTHLY,      // Hàng tháng
        QUARTERLY,    // Hàng quý
        YEARLY        // Hàng năm
    }
}


