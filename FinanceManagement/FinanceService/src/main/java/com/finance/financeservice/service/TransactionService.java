package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.Transaction;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionService {
    Optional<Transaction> getById(String id);
    Transaction createTransaction(String userId, String accountNumber, String bankBrandName,
                                  LocalDateTime transactionDate, Double amountIn, Double amountOut,
                                  String transactionContent, String referenceNumber, String bankAccountId,
                                  String category);
    Page<Transaction> filter(String userId, String accountId, String bankAccountId,
                            LocalDateTime startDate, LocalDateTime endDate,
                            String textSearch, String category, String transactionType,
                            Double minAmount, Double maxAmount,
                            String sortBy, String sortDirection,
                            int page, int size);

    Page<Transaction> filterByBankAccountIds(List<String> bankAccountIds,
                            LocalDateTime startDate, LocalDateTime endDate,
                            String textSearch, String category, String transactionType,
                            Double minAmount, Double maxAmount,
                            String sortBy, String sortDirection,
                            int page, int size);
    TransactionSummary summary(String userId, LocalDateTime startDate, LocalDateTime endDate);
    TransactionSummary summaryByBankAccountIds(List<String> bankAccountIds,
                                               LocalDateTime startDate, LocalDateTime endDate,
                                               String textSearch, String category, String transactionType,
                                               Double minAmount, Double maxAmount);
    List<TransactionStats> getTransactionStats(String userId, LocalDateTime startDate, LocalDateTime endDate);
    Optional<Transaction> updateCategory(String id, String category);
    List<Transaction> exportAll(String userId, String accountId, String bankAccountId,
                                LocalDateTime startDate, LocalDateTime endDate,
                                String textSearch, String category, String transactionType,
                                Double minAmount, Double maxAmount);

    List<Transaction> exportAllByBankAccountIds(List<String> bankAccountIds,
                                LocalDateTime startDate, LocalDateTime endDate,
                                String textSearch, String category, String transactionType,
                                Double minAmount, Double maxAmount);

    // Monthly cashflow aggregated by year and month
    List<CashflowPoint> getCashflow(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    // Dashboard summary for the Statistics screen (4 summary boxes)
    TransactionDashboardSummary dashboardSummary(String userId, LocalDateTime startDate, LocalDateTime endDate);
    
    // Category breakdown (expense/income) for selected period
    CategoryBreakdown getCategoryBreakdown(String userId, String type, LocalDateTime startDate, LocalDateTime endDate);
    
    // Category breakdown by bank account IDs (for groups)
    CategoryBreakdown getCategoryBreakdownByBankAccountIds(List<String> bankAccountIds, String type, LocalDateTime startDate, LocalDateTime endDate);

    record TransactionSummary(
            Double totalIncome, 
            Double totalExpense, 
            Double netAmount,
            long transactionCount,
            Double averageAmount
    ) {}
    
    record TransactionStats(
            String accountId,
            String accountLabel,
            Double totalIncome,
            Double totalExpense,
            long transactionCount
    ) {}

    record CashflowPoint(
            int year,
            int month,
            Double totalIncome,
            Double totalExpense,
            Double balance
    ) {}
    
    record Metric(
            Double current,
            Double previous,
            Double change,
            Double changePct,
            String direction
    ) {}

    record TransactionDashboardSummary(
            Metric totalIncome,
            Metric totalExpense,
            Metric netBalance,
            Metric averageDailyExpense,
            Metric savingRate
    ) {}
    
    record CategoryItem(
            String category,
            Double amount,
            Double percentage
    ) {}
    
    record CategoryBreakdown(
            Double totalAmount,
            List<CategoryItem> items
    ) {}
    
    record CategoryVarianceItem(
            String category,
            Double currentAmount,
            Double previousAmount,
            Double delta,
            Double deltaPercentage,
            String trend // "up", "down", "flat"
    ) {}
    
    record CategoryVariance(
            List<CategoryVarianceItem> items
    ) {}
    
    // Top biggest transactions (by expense amount)
    List<Transaction> getTopBiggestTransactions(String userId, LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    // Category variance comparison between current and previous period
    CategoryVariance getCategoryVariance(String userId, String type, LocalDateTime currentStart, LocalDateTime currentEnd, 
                                        LocalDateTime previousStart, LocalDateTime previousEnd);
    
    record DailyExpenseData(
            String date, // yyyy-MM-dd
            Double amount,
            int level // 0=null, 1=low(<30%), 2=medium(30-90%), 3=high(90-150%), 4=critical(>150%)
    ) {}
    
    record ExpenseHeatmap(
            List<DailyExpenseData> days,
            Double dailyLimit,
            String limitMode // "budget", "historical", "manual"
    ) {}
    
    // Daily expense heatmap for a month
    ExpenseHeatmap getDailyExpenseHeatmap(String userId, int year, int month, Double manualDailyLimit);
    
    // ===== GROUP STATISTICS METHODS (by bank account IDs) =====
    
    // Monthly cashflow aggregated by year and month for group
    List<CashflowPoint> getCashflowByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate);
    
    // Dashboard summary for group (4 summary boxes)
    TransactionDashboardSummary dashboardSummaryByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate);
    
    // Top biggest transactions for group
    List<Transaction> getTopBiggestTransactionsByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate, int limit);
    
    // Category variance comparison for group
    CategoryVariance getCategoryVarianceByBankAccountIds(List<String> bankAccountIds, String type, 
                                                         LocalDateTime currentStart, LocalDateTime currentEnd, 
                                                         LocalDateTime previousStart, LocalDateTime previousEnd);
    
    // Daily expense heatmap for group
    ExpenseHeatmap getDailyExpenseHeatmapByBankAccountIds(List<String> bankAccountIds, int year, int month, Double manualDailyLimit);

    // ===== REPORTS CHARTS DATA (Phase 3) =====

    record DailyTrendData(
            String date, // yyyy-MM-dd
            Double income,
            Double expense
    ) {}

    // Get category summary with filters (for reports page charts)
    CategoryBreakdown getCategorySummaryWithFilters(
            String userId, String accountId, String bankAccountId,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount);

    CategoryBreakdown getCategorySummaryWithFiltersByBankAccountIds(
            List<String> bankAccountIds,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount);

    // Get daily trend data with filters (for reports page charts)
    List<DailyTrendData> getDailyTrend(
            String userId, String accountId, String bankAccountId,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount);

    List<DailyTrendData> getDailyTrendByBankAccountIds(
            List<String> bankAccountIds,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount);
}

