package com.finance.aiservice.controller;

import com.finance.aiservice.dto.AnalyticsDashboardResponse.*;
import com.finance.aiservice.service.AnalyticsDashboardService;
import com.finance.aiservice.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Analytics Dashboard REST API Controller
 *
 * Provides structured data endpoints for AI-powered analytics dashboard.
 * All endpoints return JSON data suitable for charts and visualizations.
 *
 * Endpoints:
 * - GET /api/analytics/health-score - Financial health score (0-100)
 * - GET /api/analytics/spending-structure - Spending categories chart data
 * - GET /api/analytics/anomalies - Unusual transaction alerts
 * - GET /api/analytics/patterns - Spending pattern analysis
 * - GET /api/analytics/forecast - Budget forecast with timeline
 * - GET /api/analytics/recommendations - Discipline recommendations
 * - GET /api/analytics/dashboard - Complete dashboard (all data)
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics Dashboard", description = "AI-powered analytics dashboard APIs")
public class AnalyticsDashboardController {

    private final AnalyticsDashboardService analyticsService;
    private final ReportExportService reportExportService;

    /**
     * Get Financial Health Score
     *
     * Returns a comprehensive health score (0-100) with grade, status, and insights.
     *
     * Example Response:
     * {
     *   "score": 85,
     *   "grade": "A-",
     *   "status": "Khá tốt",
     *   "message": "Chi tiêu hợp lý, khuyến nghị tăng tiết kiệm",
     *   "strengths": ["Tiết kiệm đều đặn", "Chi tiêu có kế hoạch"],
     *   "concerns": ["Chi tiêu Giải trí cao hơn 15%"]
     * }
     */
    @GetMapping("/health-score")
    @Operation(summary = "Get financial health score", description = "Returns health score (0-100) with grade and insights")
    public ResponseEntity<HealthScore> getHealthScore(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId,
        @Parameter(description = "Year (optional, defaults to current year)", example = "2026")
        @RequestParam(required = false) Integer year,
        @Parameter(description = "Month (optional, defaults to current month, 1-12)", example = "2")
        @RequestParam(required = false) Integer month
    ) {
        log.info("GET /api/analytics/health-score - userId={}, year={}, month={}", userId, year, month);
        HealthScore healthScore = analyticsService.getFinancialHealth(userId, year, month);
        return ResponseEntity.ok(healthScore);
    }

    /**
     * Get Spending Structure
     *
     * Returns spending categories with actual vs ideal percentages for chart visualization.
     *
     * Example Response:
     * {
     *   "categories": [
     *     {"name": "Nhu cầu thiết yếu", "actualPercent": 45, "idealPercent": 50, "amount": 5000000, "status": "GOOD"},
     *     {"name": "Hưởng thụ/Giải trí", "actualPercent": 40, "idealPercent": 30, "amount": 4000000, "status": "WARNING"}
     *   ],
     *   "overallStatus": "NEEDS_ATTENTION",
     *   "recommendation": "Chi tiêu hợp lý, khuyến nghị tăng tiết kiệm"
     * }
     */
    @GetMapping("/spending-structure")
    @Operation(summary = "Get spending structure", description = "Returns spending categories with actual vs ideal percentages")
    public ResponseEntity<SpendingStructure> getSpendingStructure(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId,
        @Parameter(description = "Year (optional, defaults to current year)", example = "2026")
        @RequestParam(required = false) Integer year,
        @Parameter(description = "Month (optional, defaults to current month, 1-12)", example = "2")
        @RequestParam(required = false) Integer month
    ) {
        log.info("GET /api/analytics/spending-structure - userId={}, year={}, month={}", userId, year, month);
        SpendingStructure structure = analyticsService.getSpendingStructure(userId, year, month);
        return ResponseEntity.ok(structure);
    }

    /**
     * Get Anomaly Alerts
     *
     * Returns list of unusual transactions detected by AI.
     *
     * Example Response:
     * [
     *   {
     *     "transactionId": "123",
     *     "description": "Hadilao Hotpot",
     *     "amount": -2500000,
     *     "date": "10/01/2026",
     *     "severity": "HIGH",
     *     "reason": "Cao hơn mức thường 250%",
     *     "recommendation": "Xem xét chi tiêu ăn uống"
     *   }
     * ]
     */
    @GetMapping("/anomalies")
    @Operation(summary = "Get anomaly alerts", description = "Returns unusual transactions detected by AI")
    public ResponseEntity<List<AnomalyAlert>> getAnomalies(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId,
        @Parameter(description = "Year (optional, defaults to current year)", example = "2026")
        @RequestParam(required = false) Integer year,
        @Parameter(description = "Month (optional, defaults to current month, 1-12)", example = "2")
        @RequestParam(required = false) Integer month
    ) {
        log.info("GET /api/analytics/anomalies - userId={}, year={}, month={}", userId, year, month);
        List<AnomalyAlert> anomalies = analyticsService.getAnomalies(userId, year, month);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * Get Spending Patterns
     *
     * Returns detected spending patterns (time, day, behavior).
     *
     * Example Response:
     * [
     *   {
     *     "type": "TIME",
     *     "pattern": "20h - 22h",
     *     "description": "Không gửi tiền nhắc nhở Shopping Online",
     *     "recommendations": ["Tránh mua sắm trực tuyến buổi tối"]
     *   }
     * ]
     */
    @GetMapping("/patterns")
    @Operation(summary = "Get spending patterns", description = "Returns detected spending patterns")
    public ResponseEntity<List<SpendingPattern>> getSpendingPatterns(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId
    ) {
        log.info("GET /api/analytics/patterns - userId={}", userId);
        List<SpendingPattern> patterns = analyticsService.getSpendingPatterns(userId);
        return ResponseEntity.ok(patterns);
    }

    /**
     * Get Budget Forecast
     *
     * Returns AI-powered budget forecast with weekly timeline.
     *
     * Example Response:
     * {
     *   "forecastPeriod": "30 ngày tới",
     *   "projectedBalance": 125400000,
     *   "projectedExpense": 14257636,
     *   "trend": "INCREASING",
     *   "confidence": 85,
     *   "timeline": [
     *     {"period": "Tuần 1", "balance": 140000000, "expense": 3564409},
     *     {"period": "Tuần 2", "balance": 136435591, "expense": 3564409}
     *   ],
     *   "warning": {
     *     "severity": "MEDIUM",
     *     "message": "Cảnh báo: Dự kiến thâm hụt 4322285 ₫",
     *     "recommendation": "Cắt giảm chi tiêu không thiết yếu"
     *   }
     * }
     */
    @GetMapping("/forecast")
    @Operation(summary = "Get budget forecast", description = "Returns AI-powered budget forecast with timeline")
    public ResponseEntity<BudgetForecast> getBudgetForecast(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId
    ) {
        log.info("GET /api/analytics/forecast - userId={}", userId);
        BudgetForecast forecast = analyticsService.getBudgetForecast(userId);
        return ResponseEntity.ok(forecast);
    }

    /**
     * Get Discipline Recommendations
     *
     * Returns recommendations for improving financial discipline.
     *
     * Example Response:
     * [
     *   {
     *     "category": "Cắt giảm Mua sắm",
     *     "currentAmount": 10000000,
     *     "targetAmount": 8000000,
     *     "savingsPotential": 2000000,
     *     "priority": 1
     *   }
     * ]
     */
    @GetMapping("/recommendations")
    @Operation(summary = "Get discipline recommendations", description = "Returns recommendations for financial discipline")
    public ResponseEntity<List<DisciplineRecommendation>> getDisciplineRecommendations(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId
    ) {
        log.info("GET /api/analytics/recommendations - userId={}", userId);
        List<DisciplineRecommendation> recommendations = analyticsService.getDisciplineRecommendations(userId);
        return ResponseEntity.ok(recommendations);
    }

    /**
     * Get Complete Dashboard
     *
     * Returns all analytics data in a single response.
     * Use this endpoint to load the entire dashboard at once.
     *
     * Example Response:
     * {
     *   "healthScore": {...},
     *   "spendingStructure": {...},
     *   "anomalies": [...],
     *   "patterns": [...],
     *   "budgetForecast": {...},
     *   "recommendations": [...],
     *   "generatedAt": "2026-02-01T20:30:00"
     * }
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Get complete dashboard", description = "Returns all analytics data in one response")
    public ResponseEntity<CompleteDashboard> getCompleteDashboard(
        @Parameter(description = "User ID", required = true, example = "1")
        @RequestParam String userId
    ) {
        log.info("GET /api/analytics/dashboard - userId={}", userId);
        CompleteDashboard dashboard = analyticsService.getCompleteDashboard(userId);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Export Financial Report
     *
     * Receives already-computed dashboard data from the frontend and generates
     * a downloadable file. No recalculation — just format and return.
     *
     * @param format    "excel" → .xlsx  |  "pdf" → .pdf
     * @param month     Month number for filename/header (1-12)
     * @param year      Year for filename/header
     * @param dashboard Dashboard data already loaded on the frontend
     */
    @PostMapping("/report/export")
    @Operation(summary = "Export financial report", description = "Download financial report as Excel or PDF (no recalculation)")
    public ResponseEntity<byte[]> exportReport(
        @Parameter(description = "Format: excel or pdf", required = true, example = "excel")
        @RequestParam String format,
        @Parameter(description = "Month (1-12)", required = true, example = "2")
        @RequestParam int month,
        @Parameter(description = "Year", required = true, example = "2026")
        @RequestParam int year,
        @RequestBody CompleteDashboard dashboard
    ) {
        log.info("POST /api/analytics/report/export - format={}, month={}, year={}", format, month, year);

        try {
            if ("excel".equalsIgnoreCase(format)) {
                byte[] content = reportExportService.generateExcel(dashboard, month, year);
                String filename = String.format("bao-cao-tai-chinh-%02d-%d.xlsx", month, year);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(content);

            } else if ("pdf".equalsIgnoreCase(format)) {
                byte[] content = reportExportService.generatePdf(dashboard, month, year);
                String filename = String.format("bao-cao-tai-chinh-%02d-%d.pdf", month, year);
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .body(content);

            } else {
                log.warn("Unknown export format: {}", format);
                return ResponseEntity.badRequest().build();
            }

        } catch (Exception e) {
            log.error("Error generating {} report: {}", format, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
