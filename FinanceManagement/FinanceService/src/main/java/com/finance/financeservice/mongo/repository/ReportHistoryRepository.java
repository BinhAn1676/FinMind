package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.ReportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ReportHistory entities
 * Phase 5: Report History & SMS Delivery
 */
@Repository
public interface ReportHistoryRepository extends MongoRepository<ReportHistory, String> {

    /**
     * Find all report history for a user (paginated)
     */
    Page<ReportHistory> findByUserIdOrderByExecutedAtDesc(String userId, Pageable pageable);

    /**
     * Find all report history for a user (not paginated)
     */
    List<ReportHistory> findByUserIdOrderByExecutedAtDesc(String userId);

    /**
     * Find report history by schedule ID
     */
    List<ReportHistory> findByScheduleIdOrderByExecutedAtDesc(String scheduleId);

    /**
     * Find report history by status
     */
    List<ReportHistory> findByUserIdAndStatusOrderByExecutedAtDesc(String userId, String status);

    /**
     * Find report history within date range
     */
    List<ReportHistory> findByUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(
        String userId, LocalDateTime startDate, LocalDateTime endDate
    );

    /**
     * Delete report history older than a certain date (for cleanup)
     */
    void deleteByExecutedAtBefore(LocalDateTime date);
}
