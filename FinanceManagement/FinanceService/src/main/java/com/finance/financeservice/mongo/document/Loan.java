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
@Document(collection = "loans")
@CompoundIndex(name = "user_loan_type_idx", def = "{ 'user_id': 1, 'loan_type': 1 }")
public class Loan {
    @Id
    private String id;

    @Field("user_id")
    private String userId;

    @Field("loan_type")
    private LoanType loanType; // CHO_VAY (Lend) or VAY (Borrow)

    @Field("status")
    private LoanStatus status; // PAID, OUTDATE, ON_GOING

    // Borrower information (embedded, not separate document)
    // Changed to support multiple borrowers per loan
    @Field("borrowers")
    @Builder.Default
    private List<Borrower> borrowers = new ArrayList<>();

    // Loan core data
    @Field("principal_amount")
    private Double principalAmount; // NỢ GỐC (VNĐ)

    @Field("interest_rate")
    private Double interestRate; // LÃI SUẤT (%)

    @Field("interest_amount")
    private Double interestAmount; // TIỀN LÃI (calculated)

    @Field("start_date")
    private LocalDate startDate; // NGÀY BẮT ĐẦU

    @Field("term_days")
    private Integer termDays; // KÌ HẠN VAY (ngày)

    @Field("end_date")
    private LocalDate endDate; // NGÀY KẾT THÚC

    @Field("daily_payment_amount")
    private Double dailyPaymentAmount; // THU HÀNG NGÀY - TỔNG THU

    // Payment tracking
    @Field("payments")
    @Builder.Default
    private List<LoanPayment> payments = new ArrayList<>(); // List of payment transactions

    @Field("total_paid")
    @Builder.Default
    private Double totalPaid = 0.0; // KHÁCH ĐÃ TRẢ (sum of all payments)

    // Calculated fields (can be computed on the fly or stored)
    @Field("cumulative_principal_collected")
    private Double cumulativePrincipalCollected; // LŨY KẾ TIỀN THU KHÁCH - TIỀN GỐC

    @Field("cumulative_interest_collected")
    private Double cumulativeInterestCollected; // LŨY KẾ TIỀN THU KHÁCH - TIỀN LÃI

    @Field("cumulative_total_collected")
    private Double cumulativeTotalCollected; // LŨY KẾ TIỀN THU KHÁCH - TỔNG LŨY KẾ

    @Field("outstanding_debt")
    private Double outstandingDebt; // DƯ NỢ TÍNH ĐẾN NGÀY ĐỐI SOÁT (VNĐ)

    @Field("amount_due")
    private Double amountDue; // KHÁCH CẦN TRẢ (can be negative if overpaid)

    // Metadata
    @Field("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Field("updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Field("notes")
    private String notes; // Additional notes

    public enum LoanType {
        CHO_VAY,  // Lend (Cho Vay)
        VAY       // Borrow (Vay)
    }

    public enum LoanStatus {
        PAID,      // Đã đóng đủ
        OUTDATE,   // Chưa đóng đủ mà đã vượt quá thời hạn
        ON_GOING   // Đang diễn ra
    }

    // Embedded borrower DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Borrower {
        @Field("full_name")
        private String fullName; // HỌ VÀ TÊN

        @Field("full_name_hash")
        private String fullNameHash; // Hash for searching

        @Field("cccd")
        private String cccd; // CCCD (Citizen ID)

        @Field("cccd_hash")
        private String cccdHash; // Hash for searching

        @Field("phone_number")
        private String phoneNumber; // SỐ ĐIỆN THOẠI

        @Field("phone_number_hash")
        private String phoneNumberHash; // Hash for searching

        @Field("address")
        private String address; // ĐỊA CHỈ

        @Field("address_hash")
        private String addressHash; // Hash for searching

        @Field("additional_info")
        private String additionalInfo; // Additional field for extra information
    }

    // Embedded payment DTO
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanPayment {
        private String id; // Payment ID (no @Id annotation for embedded documents)

        @Field("payment_date")
        private LocalDate paymentDate; // Date of payment

        @Field("amount")
        private Double amount; // Payment amount

        @Field("principal_amount")
        private Double principalAmount; // Principal portion of payment

        @Field("interest_amount")
        private Double interestAmount; // Interest portion of payment

        @Field("notes")
        private String notes; // Payment notes

        @Field("created_at")
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}

