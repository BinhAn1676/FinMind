package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.ReportHistory;
import com.finance.financeservice.mongo.repository.ReportHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for viewing report execution history
 * Phase 5: Report History & SMS Delivery
 */
@RestController
@RequestMapping("/api/v1/report-history")
@RequiredArgsConstructor
@Slf4j
public class ReportHistoryController {

    private final ReportHistoryRepository historyRepository;

    /**
     * Get all report history for a user (paginated)
     * GET /api/v1/report-history?userId={userId}&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<ReportHistory>> getReportHistory(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("📜 Fetching report history for user: {} (page: {}, size: {})", userId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ReportHistory> history = historyRepository.findByUserIdOrderByExecutedAtDesc(userId, pageable);

        log.info("✅ Found {} report history records", history.getTotalElements());
        return ResponseEntity.ok(history);
    }

    /**
     * Get all report history for a user (not paginated)
     * GET /api/v1/report-history/all?userId={userId}
     */
    @GetMapping("/all")
    public ResponseEntity<List<ReportHistory>> getAllReportHistory(@RequestParam String userId) {
        log.info("📜 Fetching all report history for user: {}", userId);

        List<ReportHistory> history = historyRepository.findByUserIdOrderByExecutedAtDesc(userId);

        log.info("✅ Found {} report history records", history.size());
        return ResponseEntity.ok(history);
    }

    /**
     * Get report history by schedule ID
     * GET /api/v1/report-history/schedule/{scheduleId}
     */
    @GetMapping("/schedule/{scheduleId}")
    public ResponseEntity<List<ReportHistory>> getReportHistoryBySchedule(@PathVariable String scheduleId) {
        log.info("📜 Fetching report history for schedule: {}", scheduleId);

        List<ReportHistory> history = historyRepository.findByScheduleIdOrderByExecutedAtDesc(scheduleId);

        log.info("✅ Found {} report history records for schedule", history.size());
        return ResponseEntity.ok(history);
    }

    /**
     * Get report history by status
     * GET /api/v1/report-history/status?userId={userId}&status=success
     */
    @GetMapping("/status")
    public ResponseEntity<List<ReportHistory>> getReportHistoryByStatus(
            @RequestParam String userId,
            @RequestParam String status) {

        log.info("📜 Fetching report history for user: {} with status: {}", userId, status);

        List<ReportHistory> history = historyRepository.findByUserIdAndStatusOrderByExecutedAtDesc(userId, status);

        log.info("✅ Found {} report history records with status: {}", history.size(), status);
        return ResponseEntity.ok(history);
    }

    /**
     * Get report history within date range
     * GET /api/v1/report-history/date-range?userId={userId}&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<ReportHistory>> getReportHistoryByDateRange(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("📜 Fetching report history for user: {} from {} to {}", userId, startDate, endDate);

        List<ReportHistory> history = historyRepository.findByUserIdAndExecutedAtBetweenOrderByExecutedAtDesc(
            userId, startDate, endDate
        );

        log.info("✅ Found {} report history records in date range", history.size());
        return ResponseEntity.ok(history);
    }

    /**
     * Get single report history by ID
     * GET /api/v1/report-history/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportHistory> getReportHistoryById(@PathVariable String id) {
        log.info("📜 Fetching report history by ID: {}", id);

        return historyRepository.findById(id)
            .map(history -> {
                log.info("✅ Found report history: {}", id);
                return ResponseEntity.ok(history);
            })
            .orElseGet(() -> {
                log.warn("⚠️ Report history not found: {}", id);
                return ResponseEntity.notFound().build();
            });
    }

    /**
     * Delete report history by ID
     * DELETE /api/v1/report-history/{id}?userId={userId}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReportHistory(
            @PathVariable String id,
            @RequestParam String userId) {

        log.info("🗑️ Deleting report history: {} for user: {}", id, userId);

        // Verify ownership before deleting
        return historyRepository.findById(id)
            .<ResponseEntity<Void>>map(history -> {
                if (!history.getUserId().equals(userId)) {
                    log.warn("⚠️ User {} attempted to delete history {} owned by {}", userId, id, history.getUserId());
                    return ResponseEntity.<Void>status(403).build();
                }

                historyRepository.deleteById(id);
                log.info("✅ Report history deleted: {}", id);
                return ResponseEntity.<Void>noContent().build();
            })
            .orElseGet(() -> {
                log.warn("⚠️ Report history not found: {}", id);
                return ResponseEntity.<Void>notFound().build();
            });
    }

    /**
     * Bulk delete report history by IDs
     * DELETE /api/v1/report-history/bulk?userId={userId}
     * Body: ["id1", "id2", "id3"]
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkDeleteReportHistory(
            @RequestParam String userId,
            @RequestBody List<String> historyIds) {

        log.info("🗑️ Bulk deleting {} report histories for user: {}", historyIds.size(), userId);

        int deletedCount = 0;
        int failedCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (String id : historyIds) {
            try {
                historyRepository.findById(id).ifPresent(history -> {
                    if (history.getUserId().equals(userId)) {
                        historyRepository.deleteById(id);
                        log.debug("✅ Deleted report history: {}", id);
                    } else {
                        log.warn("⚠️ User {} attempted to delete history {} owned by {}", userId, id, history.getUserId());
                        failedIds.add(id);
                    }
                });
                deletedCount++;
            } catch (Exception e) {
                log.error("❌ Failed to delete report history: {}", id, e);
                failedCount++;
                failedIds.add(id);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("failedCount", failedCount);
        result.put("failedIds", failedIds);

        log.info("✅ Bulk delete completed: {} deleted, {} failed", deletedCount, failedCount);

        return ResponseEntity.ok(result);
    }
}
