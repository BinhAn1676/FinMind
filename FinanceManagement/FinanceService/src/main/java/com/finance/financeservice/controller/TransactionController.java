package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.service.impl.ExcelExportService;
import com.finance.financeservice.service.impl.PDFExportService;
import com.finance.financeservice.service.impl.CSVExportService;
import com.finance.financeservice.service.impl.EmailService;
import com.finance.financeservice.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final ExcelExportService excelExportService;
    private final PDFExportService pdfExportService;
    private final CSVExportService csvExportService;
    private EmailService emailService;

    public TransactionController(
            TransactionService transactionService,
            ExcelExportService excelExportService,
            PDFExportService pdfExportService,
            CSVExportService csvExportService) {
        this.transactionService = transactionService;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
        this.csvExportService = csvExportService;
    }

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    @lombok.Data
    public static class CreateTransactionRequest {
        private String accountNumber;
        private String bankBrandName;
        private String transactionDate;
        private Double amountIn;
        private Double amountOut;
        private String transactionContent;
        private String referenceNumber;
        private String bankAccountId;
        private String category;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable String id) {
        return transactionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@RequestParam("userId") String userId, @RequestBody CreateTransactionRequest request) {
        LocalDateTime transactionDate = parseDateString(request.getTransactionDate());
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        return ResponseEntity.ok(transactionService.createTransaction(
                userId,
                request.getAccountNumber(),
                request.getBankBrandName(),
                transactionDate,
                request.getAmountIn(),
                request.getAmountOut(),
                request.getTransactionContent(),
                request.getReferenceNumber(),
                request.getBankAccountId(),
                request.getCategory()
        ));
    }

    @GetMapping("/cashflow/export/excel")
    public ResponseEntity<byte[]> exportCashflowToExcel(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) throws IOException {

        // Determine target year
        int targetYear;
        if (year != null) {
            targetYear = year;
        } else {
            LocalDateTime s = parseStartDate(startDateStr);
            LocalDateTime e = parseEndDate(endDateStr);
            LocalDateTime ref = s != null ? s : (e != null ? e : LocalDateTime.now());
            targetYear = ref.getYear();
        }

        LocalDateTime yStart = LocalDate.of(targetYear, 1, 1).atStartOfDay();
        LocalDateTime yEnd = LocalDate.of(targetYear, 12, 31).atTime(23, 59, 59);

        var cashflow = transactionService.getCashflow(userId, yStart, yEnd);
        byte[] bytes = excelExportService.generateCashflowExcel(cashflow, targetYear);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "cashflow-" + targetYear + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
    @GetMapping
    public ResponseEntity<Page<Transaction>> filter(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount,
            @RequestParam(value = "sortBy", required = false, defaultValue = "transactionDate") String sortBy,
            @RequestParam(value = "sortDirection", required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        // If bankAccountIds is provided, use the new method
        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            return ResponseEntity.ok(transactionService.filterByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount, sortBy, sortDirection, page, size));
        }

        return ResponseEntity.ok(transactionService.filter(
                userId, accountId, bankAccountId, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount, sortBy, sortDirection, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<TransactionService.TransactionSummary> summary(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        // If bankAccountIds is provided, use the new method
        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            return ResponseEntity.ok(transactionService.summaryByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount));
        }

        return ResponseEntity.ok(transactionService.summary(userId, startDate, endDate));
    }

    @GetMapping("/stats")
    public ResponseEntity<List<TransactionService.TransactionStats>> getTransactionStats(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getTransactionStats(userId, startDate, endDate));
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<Transaction> updateCategory(
            @PathVariable String id,
            @RequestParam(value = "category") String category) {
        return transactionService.updateCategory(id, category)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "dateDesc") String sortOrder,
            @RequestParam(value = "includeSummarySheet", required = false, defaultValue = "true") Boolean includeSummarySheet,
            @RequestParam(value = "selectedColumns", required = false) List<String> selectedColumns) throws IOException {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        List<Transaction> transactions;

        // If bankAccountIds is provided, use the new method
        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            transactions = transactionService.exportAllByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            transactions = transactionService.exportAll(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        byte[] excelBytes = excelExportService.generateExcelFile(transactions, sortOrder, includeSummarySheet, selectedColumns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "transactions.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportToPdf(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "dateDesc") String sortOrder,
            @RequestParam(value = "includeSummarySheet", required = false, defaultValue = "true") Boolean includeSummarySheet,
            @RequestParam(value = "selectedColumns", required = false) List<String> selectedColumns) throws IOException {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        List<Transaction> transactions;

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            transactions = transactionService.exportAllByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            transactions = transactionService.exportAll(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        byte[] pdfBytes = pdfExportService.generatePdfFile(transactions, sortOrder, includeSummarySheet, selectedColumns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "transactions.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "dateDesc") String sortOrder,
            @RequestParam(value = "selectedColumns", required = false) List<String> selectedColumns) throws IOException {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        List<Transaction> transactions;

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            transactions = transactionService.exportAllByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            transactions = transactionService.exportAll(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        byte[] csvBytes = csvExportService.generateCsvFile(transactions, sortOrder, selectedColumns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.setContentDispositionFormData("attachment", "transactions.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    @PostMapping("/email-report")
    public ResponseEntity<Void> emailReport(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "dateDesc") String sortOrder,
            @RequestParam(value = "includeSummarySheet", required = false, defaultValue = "true") Boolean includeSummarySheet,
            @RequestParam(value = "selectedColumns", required = false) List<String> selectedColumns,
            @RequestParam(value = "exportFormat", required = false, defaultValue = "excel") String exportFormat,
            @RequestParam(value = "recipientEmail") String recipientEmail) throws IOException {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        List<Transaction> transactions;

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            transactions = transactionService.exportAllByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            transactions = transactionService.exportAll(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        // Generate report based on format
        byte[] reportBytes;
        String fileName;
        String formatLabel;

        switch (exportFormat.toLowerCase()) {
            case "pdf":
                reportBytes = pdfExportService.generatePdfFile(transactions, sortOrder, includeSummarySheet, selectedColumns);
                fileName = "transactions_report.pdf";
                formatLabel = "PDF";
                break;
            case "csv":
                reportBytes = csvExportService.generateCsvFile(transactions, sortOrder, selectedColumns);
                fileName = "transactions_report.csv";
                formatLabel = "CSV";
                break;
            case "excel":
            default:
                reportBytes = excelExportService.generateExcelFile(transactions, sortOrder, includeSummarySheet, selectedColumns);
                fileName = "transactions_report.xlsx";
                formatLabel = "Excel";
                break;
        }

        // Send email with report
        if (emailService != null) {
            emailService.sendReportEmail(recipientEmail, reportBytes, fileName, formatLabel);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(503)
                    .header("X-Error-Message", "Email service is not configured. Please configure spring.mail properties.")
                    .build();
        }
    }

    @GetMapping("/cashflow")
    public ResponseEntity<java.util.List<TransactionService.CashflowPoint>> getCashflow(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseDateString(startDateStr);
        LocalDateTime endDate = parseDateString(endDateStr);
        return ResponseEntity.ok(transactionService.getCashflow(userId, startDate, endDate));
    }

    // ===== REPORTS CHARTS DATA ENDPOINTS (Phase 3) =====

    @GetMapping("/category-summary")
    public ResponseEntity<TransactionService.CategoryBreakdown> getCategorySummary(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount) {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        TransactionService.CategoryBreakdown breakdown;

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            breakdown = transactionService.getCategorySummaryWithFiltersByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            breakdown = transactionService.getCategorySummaryWithFilters(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/daily-trend")
    public ResponseEntity<java.util.List<TransactionService.DailyTrendData>> getDailyTrend(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestParam(value = "bankAccountId", required = false) String bankAccountId,
            @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "textSearch", required = false) String textSearch,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "minAmount", required = false) Double minAmount,
            @RequestParam(value = "maxAmount", required = false) Double maxAmount) {

        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);

        List<TransactionService.DailyTrendData> trend;

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            trend = transactionService.getDailyTrendByBankAccountIds(
                    bankAccountIds, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        } else {
            trend = transactionService.getDailyTrend(
                    userId, accountId, bankAccountId, startDate, endDate,
                    textSearch, category, transactionType, minAmount, maxAmount);
        }

        return ResponseEntity.ok(trend);
    }

    /**
     * Parse date string to LocalDateTime
     * Supports multiple date formats:
     * - yyyy-MM-dd (2025-10-08)
     * - yyyy-MM-ddTHH:mm:ss (ISO format)
     * Returns null if string is null, empty, or invalid
     */
    private LocalDateTime parseDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try ISO date-time format first
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            // Try date-only format (yyyy-MM-dd)
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            // Set to start of day for startDate, end of day for endDate
            return date.atStartOfDay();
        } catch (Exception e) {
            // If parsing fails, try alternative formats
            try {
                // Try common date formats
                DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
                };
                
                for (DateTimeFormatter formatter : formatters) {
                    try {
                        LocalDate date = LocalDate.parse(dateStr, formatter);
                        return date.atStartOfDay();
                    } catch (Exception ignored) {
                        // Try next format
                    }
                }
            } catch (Exception ignored) {
                // Return null if all parsing attempts fail
            }
            return null;
        }
    }

    /**
     * Parse start date, normalizing to start of day (00:00:00) when only a date is provided.
     */
    private LocalDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            return date.atStartOfDay();
        } catch (Exception e) {
            // try alternative formats
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            };
            for (DateTimeFormatter f : formatters) {
                try {
                    LocalDate d = LocalDate.parse(dateStr, f);
                    return d.atStartOfDay();
                } catch (Exception ignored) { }
            }
            return null;
        }
    }

    /**
     * Parse end date, normalizing to end of day (23:59:59) when only a date is provided.
     */
    private LocalDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            return date.atTime(23, 59, 59);
        } catch (Exception e) {
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            };
            for (DateTimeFormatter f : formatters) {
                try {
                    LocalDate d = LocalDate.parse(dateStr, f);
                    return d.atTime(23, 59, 59);
                } catch (Exception ignored) { }
            }
            return null;
        }
    }
}

