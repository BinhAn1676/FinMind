package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.TransactionRepository;
import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.mysql.repository.AccountRepository;
import com.finance.financeservice.service.PlanningBudgetService;
import com.finance.financeservice.service.TransactionService;
import com.finance.financeservice.service.crypto.PiiCryptoService;
import com.finance.financeservice.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final MongoTemplate mongoTemplate;
    private final PiiCryptoService piiCryptoService;
    private final PlanningBudgetService planningBudgetService;
    private final AccountRepository accountRepository;

    @Override
    public Optional<Transaction> getById(String id) {
        return transactionRepository.findById(id).map(piiCryptoService::decryptTransaction);
    }

    @Override
    public Transaction createTransaction(String userId, String accountNumber, String bankBrandName,
                                         LocalDateTime transactionDate, Double amountIn, Double amountOut,
                                         String transactionContent, String referenceNumber, String bankAccountId,
                                         String category) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setTransactionDate(transactionDate != null ? transactionDate : LocalDateTime.now());
        transaction.setAmountIn(amountIn != null ? amountIn : 0.0);
        transaction.setAmountOut(amountOut != null ? amountOut : 0.0);
        transaction.setTransactionContent(transactionContent);
        transaction.setReferenceNumber(referenceNumber);
        transaction.setBankAccountId(bankAccountId);
        transaction.setCategory(category != null ? category : "không xác định");
        
        // Encrypt and hash PII fields
        String encAccountNumber = piiCryptoService.encrypt(userId, accountNumber);
        String encBankBrandName = piiCryptoService.encrypt(userId, bankBrandName);
        
        transaction.setAccountNumber(encAccountNumber != null ? encAccountNumber : accountNumber);
        transaction.setAccountNumberHash(HashUtils.sha256(accountNumber));
        transaction.setBankBrandName(encBankBrandName != null ? encBankBrandName : bankBrandName);
        transaction.setBankBrandNameHash(HashUtils.sha256(bankBrandName));
        
        // Determine transaction type
        if (amountIn != null && amountIn > 0 && (amountOut == null || amountOut == 0)) {
            transaction.setTransactionType("income");
        } else if (amountOut != null && amountOut > 0 && (amountIn == null || amountIn == 0)) {
            transaction.setTransactionType("expense");
        } else {
            // If both > 0 or both == 0, determine based on which is larger
            double in = amountIn != null ? amountIn : 0.0;
            double out = amountOut != null ? amountOut : 0.0;
            if (in > out) {
                transaction.setTransactionType("income");
            } else if (out > in) {
                transaction.setTransactionType("expense");
            } else {
                transaction.setTransactionType("unknown");
            }
        }
        
        // Calculate accumulated (optional, can be null for manual transactions)
        transaction.setAccumulated(null);
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        
        // Update account accumulated balance after creating transaction
        updateAccountAccumulated(bankAccountId, userId, amountIn, amountOut);
        
        return savedTransaction;
    }

    @Override
    public Page<Transaction> filter(String userId, String accountId, String bankAccountId,
                                    LocalDateTime startDate, LocalDateTime endDate,
                                    String textSearch, String category, String transactionType,
                                    Double minAmount, Double maxAmount,
                                    String sortBy, String sortDirection,
                                    int page, int size) {
        Query query = buildFilterQuery(userId, accountId, bankAccountId, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount);

        long total = mongoTemplate.count(query, Transaction.class);

        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);

        // Dynamic sorting
        org.springframework.data.domain.Sort.Direction direction =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;

        // Map sortBy to actual field name
        String sortField = mapSortField(sortBy);
        query.with(org.springframework.data.domain.Sort.by(direction, sortField));

        List<Transaction> content = mongoTemplate.find(query, Transaction.class);
        List<Transaction> decrypted = content.stream().map(piiCryptoService::decryptTransaction).toList();
        return new org.springframework.data.domain.PageImpl<>(decrypted, pageable, total);
    }

    /**
     * Map user-friendly sort field names to actual MongoDB field names.
     * Default to transactionDate if invalid field specified.
     */
    private String mapSortField(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "transactionDate";
        }

        return switch (sortBy.toLowerCase()) {
            case "date", "transactiondate" -> "transactionDate";
            case "amountin", "income" -> "amountIn";
            case "amountout", "expense" -> "amountOut";
            case "category" -> "category";
            case "merchant", "merchantname" -> "merchantName";
            default -> "transactionDate";
        };
    }

    @Override
    public TransactionSummary summary(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        Query query = new Query();
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(Criteria.where("user_id").is(userId));
        }
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            query.addCriteria(dateCriteria);
        }

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        transactions.replaceAll(piiCryptoService::decryptTransaction);

        Double totalIncome = 0.0;
        Double totalExpense = 0.0;
        int count = 0;

        for (Transaction t : transactions) {
            if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                totalIncome += t.getAmountIn();
            }
            if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                totalExpense += t.getAmountOut();
            }
            count++;
        }

        Double netAmount = totalIncome - totalExpense;
        Double averageAmount = count > 0 
                ? Math.round((netAmount / count) * 100.0) / 100.0
                : 0.0;
        var result = new TransactionSummary(totalIncome, totalExpense, netAmount, count, averageAmount);
        System.out.println(result);
        return result;
    }

    @Override
    public TransactionSummary summaryByBankAccountIds(List<String> bankAccountIds,
                                                       LocalDateTime startDate, LocalDateTime endDate,
                                                       String textSearch, String category, String transactionType,
                                                       Double minAmount, Double maxAmount) {
        Query query = buildFilterQueryByBankAccountIds(bankAccountIds, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount);

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        transactions.replaceAll(piiCryptoService::decryptTransaction);

        Double totalIncome = 0.0;
        Double totalExpense = 0.0;
        int count = 0;

        for (Transaction t : transactions) {
            if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                totalIncome += t.getAmountIn();
            }
            if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                totalExpense += t.getAmountOut();
            }
            count++;
        }

        Double netAmount = totalIncome - totalExpense;
        Double averageAmount = count > 0 
                ? Math.round((netAmount / count) * 100.0) / 100.0
                : 0.0;

        return new TransactionSummary(totalIncome, totalExpense, netAmount, count, averageAmount);
    }

    @Override
    public List<TransactionStats> getTransactionStats(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        Query query = new Query();
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(Criteria.where("user_id").is(userId));
        }
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            query.addCriteria(dateCriteria);
        }

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        transactions.replaceAll(piiCryptoService::decryptTransaction);

        java.util.Map<String, TransactionStats> statsMap = new java.util.HashMap<>();

        for (Transaction t : transactions) {
            String accountId = t.getBankAccountId() != null ? t.getBankAccountId() : "unknown";
            
            TransactionStats stats = statsMap.getOrDefault(accountId, 
                new TransactionStats(accountId, t.getAccountNumber() != null ? t.getAccountNumber() : "Unknown",
                        0.0, 0.0, 0));

            Double income = stats.totalIncome();
            Double expense = stats.totalExpense();
            long count = stats.transactionCount();

            if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                income += t.getAmountIn();
            }
            if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                expense += t.getAmountOut();
            }
            count++;

            statsMap.put(accountId, new TransactionStats(stats.accountId(), stats.accountLabel(),
                    income, expense, count));
        }

        return new ArrayList<>(statsMap.values());
    }

    @Override
    public Optional<Transaction> updateCategory(String id, String category) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(id);
        if (transactionOpt.isPresent()) {
            Transaction transaction = transactionOpt.get();
            String oldCategory = transaction.getCategory();
            transaction.setCategory(category);
            transactionRepository.save(transaction);

            // reflect re-categorization into planning budgets (for both expenses and income)
            if (transaction.getTransactionDate() != null) {
                java.time.LocalDate when = transaction.getTransactionDate().toLocalDate();
                
                // Handle expense (amountOut)
                Double amountOut = transaction.getAmountOut() != null ? transaction.getAmountOut() : 0.0;
                if (amountOut > 0) {
                    planningBudgetService.moveExpense(transaction.getUserId(), oldCategory, category, amountOut, when);
                }
                
                // Handle income (amountIn) - for income planning like "Lương"
                Double amountIn = transaction.getAmountIn() != null ? transaction.getAmountIn() : 0.0;
                if (amountIn > 0) {
                    planningBudgetService.moveExpense(transaction.getUserId(), oldCategory, category, amountIn, when);
                }
            }
            return Optional.of(transaction);
        }
        return Optional.empty();
    }

    @Override
    public List<Transaction> exportAll(String userId, String accountId, String bankAccountId,
                                       LocalDateTime startDate, LocalDateTime endDate,
                                       String textSearch, String category, String transactionType,
                                       Double minAmount, Double maxAmount) {
        Query query = buildFilterQuery(userId, accountId, bankAccountId, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount);

        // Sort by transaction date descending
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "transaction_date"));

        // No pagination - return all results
        List<Transaction> list = mongoTemplate.find(query, Transaction.class);
        list.replaceAll(piiCryptoService::decryptTransaction);
        return list;
    }

    @Override
    public Page<Transaction> filterByBankAccountIds(List<String> bankAccountIds,
                                                    LocalDateTime startDate, LocalDateTime endDate,
                                                    String textSearch, String category, String transactionType,
                                                    Double minAmount, Double maxAmount,
                                                    String sortBy, String sortDirection,
                                                    int page, int size) {
        Query query = buildFilterQueryByBankAccountIds(bankAccountIds, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount);

        long total = mongoTemplate.count(query, Transaction.class);

        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);

        // Dynamic sorting
        org.springframework.data.domain.Sort.Direction direction =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;

        String sortField = mapSortField(sortBy);
        query.with(org.springframework.data.domain.Sort.by(direction, sortField));

        List<Transaction> content = mongoTemplate.find(query, Transaction.class);
        List<Transaction> decrypted = content.stream().map(piiCryptoService::decryptTransaction).toList();
        return new org.springframework.data.domain.PageImpl<>(decrypted, pageable, total);
    }

    @Override
    public List<Transaction> exportAllByBankAccountIds(List<String> bankAccountIds,
                                                        LocalDateTime startDate, LocalDateTime endDate,
                                                        String textSearch, String category, String transactionType,
                                                        Double minAmount, Double maxAmount) {
        Query query = buildFilterQueryByBankAccountIds(bankAccountIds, startDate, endDate,
                textSearch, category, transactionType, minAmount, maxAmount);

        // Sort by transaction date descending
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "transaction_date"));

        // No pagination - return all results
        List<Transaction> list = mongoTemplate.find(query, Transaction.class);
        list.replaceAll(piiCryptoService::decryptTransaction);
        return list;
    }

    @Override
    public List<CashflowPoint> getCashflow(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        // Compute the target year: prefer startDate's year, then endDate's year, fallback to current
        int targetYear = (startDate != null ? startDate.getYear() : (endDate != null ? endDate.getYear() : java.time.LocalDate.now().getYear()));

        // Always bound to whole year so day/month changes do not affect results
        java.time.LocalDateTime yearStart = java.time.LocalDate.of(targetYear, 1, 1).atStartOfDay();
        java.time.LocalDateTime yearEnd = java.time.LocalDate.of(targetYear, 12, 31).atTime(23, 59, 59);

        Query query = new Query();
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(Criteria.where("user_id").is(userId));
        }
        query.addCriteria(Criteria.where("transaction_date").gte(yearStart).lte(yearEnd));

        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        transactions.replaceAll(piiCryptoService::decryptTransaction);

        java.util.Map<String, CashflowPoint> byYm = new java.util.TreeMap<>();

        for (Transaction t : transactions) {
            if (t.getTransactionDate() == null) continue;
            java.time.LocalDateTime dt = t.getTransactionDate();
            int y = dt.getYear();
            int m = dt.getMonthValue();
            String key = y + "-" + (m < 10 ? ("0" + m) : String.valueOf(m));

            CashflowPoint current = byYm.getOrDefault(key, new CashflowPoint(y, m, 0.0, 0.0, 0.0));

            double income = current.totalIncome();
            double expense = current.totalExpense();
            if (t.getAmountIn() != null && t.getAmountIn() > 0) income += t.getAmountIn();
            if (t.getAmountOut() != null && t.getAmountOut() > 0) expense += t.getAmountOut();

            double balance = income - expense;
            byYm.put(key, new CashflowPoint(y, m, income, expense, balance));
        }

        return new ArrayList<>(byYm.values());
    }
    
    @Override
    public TransactionDashboardSummary dashboardSummary(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        // Default to current month if no range supplied
        LocalDate today = LocalDate.now();
        LocalDateTime currentStart = startDate != null
                ? startDate
                : today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime currentEnd = endDate != null
                ? endDate
                : today.withDayOfMonth(today.lengthOfMonth()).atTime(LocalTime.MAX.withNano(0));

        long periodDays = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1;
        if (periodDays <= 0) {
            periodDays = 1;
        }

        TransactionSummary current = summary(userId, currentStart, currentEnd);

        LocalDateTime previousEnd = currentStart.minusSeconds(1);
        LocalDateTime previousStart = previousEnd.minusDays(periodDays - 1);
        TransactionSummary previous = summary(userId, previousStart, previousEnd);

        Metric incomeMetric = buildMetric(current.totalIncome(), previous.totalIncome());
        Metric expenseMetric = buildMetric(current.totalExpense(), previous.totalExpense());
        Metric netMetric = buildMetric(current.netAmount(), previous.netAmount());

        double avgExpenseCurrent = safeDivide(current.totalExpense(), periodDays);
        double avgExpensePrevious = safeDivide(previous.totalExpense(), periodDays);
        Metric avgDailyExpenseMetric = buildMetric(avgExpenseCurrent, avgExpensePrevious);

        Double savingRateCurrent = (current.totalIncome() != null && current.totalIncome() != 0)
                ? current.netAmount() / current.totalIncome()
                : 0.0;
        Double savingRatePrevious = (previous.totalIncome() != null && previous.totalIncome() != 0)
                ? previous.netAmount() / previous.totalIncome()
                : 0.0;
        Metric savingRateMetric = buildMetric(savingRateCurrent, savingRatePrevious);

        return new TransactionDashboardSummary(
                incomeMetric,
                expenseMetric,
                netMetric,
                avgDailyExpenseMetric,
                savingRateMetric
        );
    }
    
    @Override
    public CategoryBreakdown getCategoryBreakdown(String userId, String type, LocalDateTime startDate, LocalDateTime endDate) {
        boolean isExpense = "expense".equalsIgnoreCase(type);
        boolean isIncome = "income".equalsIgnoreCase(type);
        if (!isExpense && !isIncome) {
            throw new IllegalArgumentException("type must be 'expense' or 'income'");
        }

        // Build match criteria
        List<Criteria> matchCriteria = new ArrayList<>();
        if (userId != null && !userId.isEmpty()) {
            matchCriteria.add(Criteria.where("user_id").is(userId));
        }
        // Combine startDate and endDate into a single transaction_date criteria
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            matchCriteria.add(dateCriteria);
        }
        // Filter by transaction_type if available
        matchCriteria.add(Criteria.where("transaction_type").is(type));
        // Also filter by amount field: expense should have amountOut > 0, income should have amountIn > 0
        if (isExpense) {
            matchCriteria.add(Criteria.where("amount_out").gt(0));
        } else {
            matchCriteria.add(Criteria.where("amount_in").gt(0));
        }

        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));

        // Project: select category (with default for null) and amount field
        String amountField = isExpense ? "amount_out" : "amount_in";
        ProjectionOperation projectStage = Aggregation.project()
                .andExpression("ifNull(category, 'không xác định')").as("category")
                .and(amountField).as("amount");

        // Group by category and sum amounts
        GroupOperation groupStage = Aggregation.group("$category")
                .sum("$amount").as("totalAmount");

        // Sort by totalAmount descending
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "totalAmount");

        // Execute aggregation
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                projectStage,
                groupStage,
                sortStage
        );

        // Result DTO for aggregation output
        record CategoryAggregationResult(String _id, Double totalAmount) {}

        AggregationResults<CategoryAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", CategoryAggregationResult.class
        );

        List<CategoryAggregationResult> aggregationResults = results.getMappedResults();

        // Calculate total and percentages
        double total = aggregationResults.stream()
                .mapToDouble(r -> r.totalAmount() != null ? r.totalAmount() : 0.0)
                .sum();

        double finalTotal = total;
        List<CategoryItem> items = aggregationResults.stream()
                .map(r -> {
                    String category = (r._id() == null || r._id().isBlank()) ? "không xác định" : r._id();
                    double amount = r.totalAmount() != null ? r.totalAmount() : 0.0;
                    double pct = finalTotal > 0 ? (amount / finalTotal) * 100.0 : 0.0;
                    return new CategoryItem(category, amount, pct);
                })
                .toList();

        return new CategoryBreakdown(total, items);
    }
    
    @Override
    public CategoryBreakdown getCategoryBreakdownByBankAccountIds(List<String> bankAccountIds, String type, LocalDateTime startDate, LocalDateTime endDate) {
        boolean isExpense = "expense".equalsIgnoreCase(type);
        boolean isIncome = "income".equalsIgnoreCase(type);
        if (!isExpense && !isIncome) {
            throw new IllegalArgumentException("type must be 'expense' or 'income'");
        }
        
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return new CategoryBreakdown(0.0, new ArrayList<>());
        }

        // Build match criteria
        List<Criteria> matchCriteria = new ArrayList<>();
        matchCriteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
        
        // Combine startDate and endDate into a single transaction_date criteria
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            matchCriteria.add(dateCriteria);
        }
        // Filter by transaction_type if available
        matchCriteria.add(Criteria.where("transaction_type").is(type));
        // Also filter by amount field: expense should have amountOut > 0, income should have amountIn > 0
        if (isExpense) {
            matchCriteria.add(Criteria.where("amount_out").gt(0));
        } else {
            matchCriteria.add(Criteria.where("amount_in").gt(0));
        }

        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));

        // Project: select category (with default for null) and amount field
        String amountField = isExpense ? "amount_out" : "amount_in";
        ProjectionOperation projectStage = Aggregation.project()
                .andExpression("ifNull(category, 'không xác định')").as("category")
                .and(amountField).as("amount");

        // Group by category and sum amounts
        GroupOperation groupStage = Aggregation.group("$category")
                .sum("$amount").as("totalAmount");

        // Sort by totalAmount descending
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "totalAmount");

        // Execute aggregation
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                projectStage,
                groupStage,
                sortStage
        );

        // Result DTO for aggregation output
        record CategoryAggregationResult(String _id, Double totalAmount) {}

        AggregationResults<CategoryAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", CategoryAggregationResult.class
        );

        List<CategoryAggregationResult> aggregationResults = results.getMappedResults();

        // Calculate total and percentages
        double total = aggregationResults.stream()
                .mapToDouble(r -> r.totalAmount() != null ? r.totalAmount() : 0.0)
                .sum();

        double finalTotal = total;
        List<CategoryItem> items = aggregationResults.stream()
                .map(r -> {
                    String category = (r._id() == null || r._id().isBlank()) ? "không xác định" : r._id();
                    double amount = r.totalAmount() != null ? r.totalAmount() : 0.0;
                    double pct = finalTotal > 0 ? (amount / finalTotal) * 100.0 : 0.0;
                    return new CategoryItem(category, amount, pct);
                })
                .toList();

        return new CategoryBreakdown(total, items);
    }

    private Metric buildMetric(Double current, Double previous) {
        double currentVal = current != null ? current : 0.0;
        double previousVal = previous != null ? previous : 0.0;
        double change = currentVal - previousVal;
        Double changePct = previousVal != 0.0 ? (change / previousVal) * 100.0 : null;
        String direction = change > 0 ? "up" : (change < 0 ? "down" : "flat");
        return new Metric(currentVal, previousVal, change, changePct, direction);
    }

    private double safeDivide(Double value, long divider) {
        double v = value != null ? value : 0.0;
        return divider > 0 ? v / divider : 0.0;
    }

    private Query buildFilterQueryByBankAccountIds(List<String> bankAccountIds,
                                                    LocalDateTime startDate, LocalDateTime endDate,
                                                    String textSearch, String category, String transactionType,
                                                    Double minAmount, Double maxAmount) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            criteriaList.add(Criteria.where("bank_account_id").in(bankAccountIds));
        }

        if (startDate != null) {
            criteriaList.add(Criteria.where("transaction_date").gte(startDate));
        }

        if (endDate != null) {
            criteriaList.add(Criteria.where("transaction_date").lte(endDate));
        }

        if (textSearch != null && !textSearch.isEmpty()) {
            String searchPattern = ".*" + textSearch + ".*";
            String hash = HashUtils.sha256(textSearch);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("transaction_content").regex(searchPattern, "i"),
                    Criteria.where("reference_number").regex(searchPattern, "i"),
                    Criteria.where("account_number_hash").is(hash),
                    Criteria.where("bank_brand_name_hash").is(hash)
            ));
        }

        // Category filter - exact match (case-insensitive)
        if (category != null && !category.trim().isEmpty()) {
            criteriaList.add(Criteria.where("category").regex("^" + category.trim() + "$", "i"));
        }

        if (transactionType != null && !transactionType.trim().isEmpty()) {
            String type = transactionType.trim();
            criteriaList.add(Criteria.where("transaction_type").is(type));
        }

        // Amount filter: match if amount_in OR amount_out is within range
        if (minAmount != null || maxAmount != null) {
            List<Criteria> amountCriteriaList = new ArrayList<>();
            
            // Build criteria for amount_in
            Criteria amountInCriteria = Criteria.where("amount_in");
            if (minAmount != null && maxAmount != null) {
                amountInCriteria = amountInCriteria.gte(minAmount).lte(maxAmount);
            } else if (minAmount != null) {
                amountInCriteria = amountInCriteria.gte(minAmount);
            } else if (maxAmount != null) {
                amountInCriteria = amountInCriteria.lte(maxAmount);
            }
            amountCriteriaList.add(amountInCriteria);
            
            // Build criteria for amount_out
            Criteria amountOutCriteria = Criteria.where("amount_out");
            if (minAmount != null && maxAmount != null) {
                amountOutCriteria = amountOutCriteria.gte(minAmount).lte(maxAmount);
            } else if (minAmount != null) {
                amountOutCriteria = amountOutCriteria.gte(minAmount);
            } else if (maxAmount != null) {
                amountOutCriteria = amountOutCriteria.lte(maxAmount);
            }
            amountCriteriaList.add(amountOutCriteria);
            
            // Match if either amount_in OR amount_out satisfies the condition
            criteriaList.add(new Criteria().orOperator(amountCriteriaList.toArray(new Criteria[0])));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }

    private Query buildFilterQuery(String userId, String accountId, String bankAccountId,
                                  LocalDateTime startDate, LocalDateTime endDate,
                                  String textSearch, String category, String transactionType,
                                  Double minAmount, Double maxAmount) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (userId != null && !userId.isEmpty()) {
            criteriaList.add(Criteria.where("user_id").is(userId));
        }

        if (accountId != null && !accountId.isEmpty()) {
            criteriaList.add(Criteria.where("bank_account_id").is(accountId));
        }

        if (bankAccountId != null && !bankAccountId.isEmpty()) {
            criteriaList.add(Criteria.where("bank_account_id").is(bankAccountId));
        }

        if (startDate != null) {
            criteriaList.add(Criteria.where("transaction_date").gte(startDate));
        }

        if (endDate != null) {
            criteriaList.add(Criteria.where("transaction_date").lte(endDate));
        }

        if (textSearch != null && !textSearch.isEmpty()) {
            String searchPattern = ".*" + textSearch + ".*";
            String hash = HashUtils.sha256(textSearch);
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("transaction_content").regex(searchPattern, "i"),
                    Criteria.where("reference_number").regex(searchPattern, "i"),
                    Criteria.where("account_number_hash").is(hash),
                    Criteria.where("bank_brand_name_hash").is(hash)
            ));
        }

        // Category filter - exact match (case-insensitive)
        if (category != null && !category.trim().isEmpty()) {
            criteriaList.add(Criteria.where("category").regex("^" + category.trim() + "$", "i"));
        }

        if (transactionType != null && !transactionType.trim().isEmpty()) {
            String type = transactionType.trim();
            criteriaList.add(Criteria.where("transaction_type").is(type));
        }

        // Amount filter: match if amount_in OR amount_out is within range
        if (minAmount != null || maxAmount != null) {
            List<Criteria> amountCriteriaList = new ArrayList<>();
            
            // Build criteria for amount_in
            Criteria amountInCriteria = Criteria.where("amount_in");
            if (minAmount != null && maxAmount != null) {
                amountInCriteria = amountInCriteria.gte(minAmount).lte(maxAmount);
            } else if (minAmount != null) {
                amountInCriteria = amountInCriteria.gte(minAmount);
            } else if (maxAmount != null) {
                amountInCriteria = amountInCriteria.lte(maxAmount);
            }
            amountCriteriaList.add(amountInCriteria);
            
            // Build criteria for amount_out
            Criteria amountOutCriteria = Criteria.where("amount_out");
            if (minAmount != null && maxAmount != null) {
                amountOutCriteria = amountOutCriteria.gte(minAmount).lte(maxAmount);
            } else if (minAmount != null) {
                amountOutCriteria = amountOutCriteria.gte(minAmount);
            } else if (maxAmount != null) {
                amountOutCriteria = amountOutCriteria.lte(maxAmount);
            }
            amountCriteriaList.add(amountOutCriteria);
            
            // Combine with OR: match if either amount_in OR amount_out is in range
            criteriaList.add(new Criteria().orOperator(amountCriteriaList.toArray(new Criteria[0])));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }
    
    /**
     * Update account accumulated balance when a transaction is created
     */
    private void updateAccountAccumulated(String bankAccountId, String userId, Double amountIn, Double amountOut) {
        if (bankAccountId == null || bankAccountId.isEmpty() || userId == null || userId.isEmpty()) {
            return;
        }

        try {
            Account account = accountRepository.findByBankAccountIdAndUserId(bankAccountId, userId);
            if (account != null) {
                double current = parseDouble(account.getAccumulated());
                double inVal = amountIn != null ? amountIn : 0.0;
                double outVal = amountOut != null ? amountOut : 0.0;
                double updated = current + inVal - outVal;
                String accumulatedStr = String.format("%.2f", updated);
                account.setAccumulated(accumulatedStr);
                accountRepository.save(account);
                log.debug("Updated accumulated for account {}: {} (current: {}, +in: {}, -out: {})",
                        bankAccountId, accumulatedStr, current, inVal, outVal);
            } else {
                log.warn("Account not found for bankAccountId: {} and userId: {}", bankAccountId, userId);
            }
        } catch (Exception e) {
            log.error("Error updating accumulated for account {}: {}", bankAccountId, e.getMessage(), e);
        }
    }
    
    /**
     * Parse double from string, return 0.0 if invalid
     */
    private double parseDouble(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid accumulated value: {}, using 0.0", value);
            return 0.0;
        }
    }
    
    @Override
    public List<Transaction> getTopBiggestTransactions(String userId, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        // Build match criteria
        List<Criteria> matchCriteria = new ArrayList<>();
        if (userId != null && !userId.isEmpty()) {
            matchCriteria.add(Criteria.where("user_id").is(userId));
        }
        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            matchCriteria.add(dateCriteria);
        }
        // Only expenses (amount_out > 0)
        matchCriteria.add(Criteria.where("amount_out").gt(0));

        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));
        
        // Sort by amount_out descending
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "amount_out");
        
        // Limit to top N
        LimitOperation limitStage = Aggregation.limit(limit);
        
        // Execute aggregation
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                sortStage,
                limitStage
        );
        
        AggregationResults<Transaction> results = mongoTemplate.aggregate(
                aggregation, "transactions", Transaction.class
        );
        
        List<Transaction> transactions = results.getMappedResults();
        
        // Decrypt transactions
        return transactions.stream()
                .map(piiCryptoService::decryptTransaction)
                .toList();
    }
    
    @Override
    public CategoryVariance getCategoryVariance(String userId, String type, 
                                               LocalDateTime currentStart, LocalDateTime currentEnd,
                                               LocalDateTime previousStart, LocalDateTime previousEnd) {
        // Get breakdown for current period
        CategoryBreakdown currentBreakdown = getCategoryBreakdown(userId, type, currentStart, currentEnd);
        
        // Get breakdown for previous period
        CategoryBreakdown previousBreakdown = getCategoryBreakdown(userId, type, previousStart, previousEnd);
        
        // Create maps for easy lookup
        Map<String, Double> currentMap = currentBreakdown.items().stream()
                .collect(Collectors.toMap(CategoryItem::category, CategoryItem::amount));
        
        Map<String, Double> previousMap = previousBreakdown.items().stream()
                .collect(Collectors.toMap(CategoryItem::category, CategoryItem::amount));
        
        // Get all categories (union of both periods)
        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(currentMap.keySet());
        allCategories.addAll(previousMap.keySet());
        
        // Calculate variance for each category
        List<CategoryVarianceItem> items = allCategories.stream()
                .map(category -> {
                    Double currentAmount = currentMap.getOrDefault(category, 0.0);
                    Double previousAmount = previousMap.getOrDefault(category, 0.0);
                    Double delta = currentAmount - previousAmount;
                    
                    // Calculate percentage change
                    Double deltaPercentage;
                    if (previousAmount == 0) {
                        deltaPercentage = currentAmount > 0 ? 100.0 : 0.0;
                    } else {
                        deltaPercentage = (delta / previousAmount) * 100.0;
                    }
                    
                    // Determine trend
                    String trend;
                    if (Math.abs(delta) < 0.01) {
                        trend = "flat";
                    } else if (delta > 0) {
                        trend = "up";
                    } else {
                        trend = "down";
                    }
                    
                    return new CategoryVarianceItem(
                            category,
                            currentAmount,
                            previousAmount,
                            delta,
                            deltaPercentage,
                            trend
                    );
                })
                // Sort by absolute delta (highest variance first)
                .sorted((a, b) -> Double.compare(Math.abs(b.delta()), Math.abs(a.delta())))
                .toList();
        
        return new CategoryVariance(items);
    }
    
    @Override
    public ExpenseHeatmap getDailyExpenseHeatmap(String userId, int year, int month, Double manualDailyLimit) {
        // Calculate daily limit
        Double dailyLimit;
        String limitMode;
        
        if (manualDailyLimit != null && manualDailyLimit > 0) {
            dailyLimit = manualDailyLimit;
            limitMode = "manual";
        } else {
            // Calculate from historical average (last 3 months)
            LocalDateTime threeMonthsAgo = LocalDateTime.of(year, month, 1, 0, 0).minusMonths(3);
            LocalDateTime endOfLastMonth = LocalDateTime.of(year, month, 1, 0, 0).minusDays(1).withHour(23).withMinute(59).withSecond(59);
            
            List<Criteria> historicalCriteria = new ArrayList<>();
            historicalCriteria.add(Criteria.where("user_id").is(userId));
            historicalCriteria.add(Criteria.where("transaction_date").gte(threeMonthsAgo).lte(endOfLastMonth));
            historicalCriteria.add(Criteria.where("amount_out").gt(0));
            
            MatchOperation historicalMatch = Aggregation.match(new Criteria().andOperator(historicalCriteria.toArray(new Criteria[0])));
            GroupOperation historicalGroup = Aggregation.group().sum("amount_out").as("totalExpense");
            
            Aggregation historicalAgg = Aggregation.newAggregation(historicalMatch, historicalGroup);
            
            record HistoricalResult(Double totalExpense) {}
            AggregationResults<HistoricalResult> historicalResults = mongoTemplate.aggregate(
                    historicalAgg, "transactions", HistoricalResult.class
            );
            
            Double totalHistorical = historicalResults.getMappedResults().isEmpty() ? 0.0 
                    : historicalResults.getMappedResults().get(0).totalExpense();
            
            // Average per day over 90 days
            dailyLimit = totalHistorical / 90.0;
            limitMode = "historical";
            
            // If historical is 0, use a default
            if (dailyLimit == 0) {
                dailyLimit = 500000.0; // Default 500k VND
                limitMode = "default";
            }
        }
        
        // Get daily expenses for the specified month
        LocalDateTime startOfMonth = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusSeconds(1);
        
        List<Criteria> matchCriteria = new ArrayList<>();
        matchCriteria.add(Criteria.where("user_id").is(userId));
        matchCriteria.add(Criteria.where("transaction_date").gte(startOfMonth).lte(endOfMonth));
        matchCriteria.add(Criteria.where("amount_out").gt(0));
        
        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));
        
        // Project to extract date (yyyy-MM-dd) and amount_out
        ProjectionOperation projectStage = Aggregation.project("amount_out")
                .andExpression("dateToString('%Y-%m-%d', transaction_date)").as("dateStr");
        
        // Group by dateStr and sum amounts
        GroupOperation groupStage = Aggregation.group("dateStr")
                .sum("amount_out").as("totalAmount");
        
        SortOperation sortStage = Aggregation.sort(Sort.Direction.ASC, "_id");
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                projectStage,
                groupStage,
                sortStage
        );
        
        record DailyAggregationResult(String _id, Double totalAmount) {}
        AggregationResults<DailyAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", DailyAggregationResult.class
        );
        
        Map<String, Double> dailyExpenseMap = results.getMappedResults().stream()
                .collect(Collectors.toMap(DailyAggregationResult::_id, DailyAggregationResult::totalAmount));
        
        // Build complete list of days for the month
        int daysInMonth = startOfMonth.toLocalDate().lengthOfMonth();
        List<DailyExpenseData> days = new ArrayList<>();
        
        for (int day = 1; day <= daysInMonth; day++) {
            String dateStr = String.format("%d-%02d-%02d", year, month, day);
            Double amount = dailyExpenseMap.getOrDefault(dateStr, 0.0);
            int level = calculateExpenseLevel(amount, dailyLimit);
            days.add(new DailyExpenseData(dateStr, amount, level));
        }
        
        return new ExpenseHeatmap(days, dailyLimit, limitMode);
    }
    
    private int calculateExpenseLevel(Double amount, Double dailyLimit) {
        if (amount == null || amount == 0) {
            return 0; // null/zero
        }
        
        double percentage = (amount / dailyLimit) * 100.0;
        
        if (percentage < 30) {
            return 1; // low (green light)
        } else if (percentage < 90) {
            return 2; // medium (green)
        } else if (percentage < 150) {
            return 3; // high (orange/red light)
        } else {
            return 4; // critical (red)
        }
    }
    
    // ===== GROUP STATISTICS METHODS (by bank account IDs) =====
    
    @Override
    public List<CashflowPoint> getCashflowByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Compute the target year: prefer startDate's year, then endDate's year, fallback to current
        int targetYear = (startDate != null ? startDate.getYear() : (endDate != null ? endDate.getYear() : LocalDate.now().getYear()));
        
        // Always bound to whole year so day/month changes do not affect results
        LocalDateTime yearStart = LocalDate.of(targetYear, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(targetYear, 12, 31).atTime(23, 59, 59);
        
        Query query = new Query();
        query.addCriteria(Criteria.where("bank_account_id").in(bankAccountIds));
        query.addCriteria(Criteria.where("transaction_date").gte(yearStart).lte(yearEnd));
        
        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        transactions.replaceAll(piiCryptoService::decryptTransaction);
        
        Map<String, CashflowPoint> byYm = new TreeMap<>();
        
        for (Transaction t : transactions) {
            if (t.getTransactionDate() == null) continue;
            LocalDateTime dt = t.getTransactionDate();
            int y = dt.getYear();
            int m = dt.getMonthValue();
            String key = y + "-" + (m < 10 ? ("0" + m) : String.valueOf(m));
            
            CashflowPoint current = byYm.getOrDefault(key, new CashflowPoint(y, m, 0.0, 0.0, 0.0));
            
            double income = current.totalIncome();
            double expense = current.totalExpense();
            if (t.getAmountIn() != null && t.getAmountIn() > 0) income += t.getAmountIn();
            if (t.getAmountOut() != null && t.getAmountOut() > 0) expense += t.getAmountOut();
            
            double balance = income - expense;
            byYm.put(key, new CashflowPoint(y, m, income, expense, balance));
        }
        
        return new ArrayList<>(byYm.values());
    }
    
    @Override
    public TransactionDashboardSummary dashboardSummaryByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate) {
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            Metric emptyMetric = new Metric(0.0, 0.0, 0.0, null, "flat");
            return new TransactionDashboardSummary(emptyMetric, emptyMetric, emptyMetric, emptyMetric, emptyMetric);
        }
        
        // Default to current month if no range supplied
        LocalDate today = LocalDate.now();
        LocalDateTime currentStart = startDate != null
                ? startDate
                : today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime currentEnd = endDate != null
                ? endDate
                : today.withDayOfMonth(today.lengthOfMonth()).atTime(LocalTime.MAX.withNano(0));
        
        long periodDays = ChronoUnit.DAYS.between(currentStart, currentEnd) + 1;
        if (periodDays <= 0) {
            periodDays = 1;
        }
        
        TransactionSummary current = summaryByBankAccountIds(bankAccountIds, currentStart, currentEnd, null, null, null, null, null);

        LocalDateTime previousEnd = currentStart.minusSeconds(1);
        LocalDateTime previousStart = previousEnd.minusDays(periodDays - 1);
        TransactionSummary previous = summaryByBankAccountIds(bankAccountIds, previousStart, previousEnd, null, null, null, null, null);
        
        Metric incomeMetric = buildMetric(current.totalIncome(), previous.totalIncome());
        Metric expenseMetric = buildMetric(current.totalExpense(), previous.totalExpense());
        Metric netMetric = buildMetric(current.netAmount(), previous.netAmount());
        
        double avgExpenseCurrent = safeDivide(current.totalExpense(), periodDays);
        double avgExpensePrevious = safeDivide(previous.totalExpense(), periodDays);
        Metric avgDailyExpenseMetric = buildMetric(avgExpenseCurrent, avgExpensePrevious);
        
        Double savingRateCurrent = (current.totalIncome() != null && current.totalIncome() != 0)
                ? current.netAmount() / current.totalIncome()
                : 0.0;
        Double savingRatePrevious = (previous.totalIncome() != null && previous.totalIncome() != 0)
                ? previous.netAmount() / previous.totalIncome()
                : 0.0;
        Metric savingRateMetric = buildMetric(savingRateCurrent, savingRatePrevious);
        
        return new TransactionDashboardSummary(
                incomeMetric,
                expenseMetric,
                netMetric,
                avgDailyExpenseMetric,
                savingRateMetric
        );
    }
    
    @Override
    public List<Transaction> getTopBiggestTransactionsByBankAccountIds(List<String> bankAccountIds, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Build match criteria
        List<Criteria> matchCriteria = new ArrayList<>();
        matchCriteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
        
        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) {
                dateCriteria = dateCriteria.gte(startDate);
            }
            if (endDate != null) {
                dateCriteria = dateCriteria.lte(endDate);
            }
            matchCriteria.add(dateCriteria);
        }
        // Only expenses (amount_out > 0)
        matchCriteria.add(Criteria.where("amount_out").gt(0));
        
        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));
        
        // Sort by amount_out descending
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "amount_out");
        
        // Limit to top N
        LimitOperation limitStage = Aggregation.limit(limit);
        
        // Execute aggregation
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                sortStage,
                limitStage
        );
        
        AggregationResults<Transaction> results = mongoTemplate.aggregate(
                aggregation, "transactions", Transaction.class
        );
        
        List<Transaction> transactions = results.getMappedResults();
        
        // Decrypt transactions
        return transactions.stream()
                .map(piiCryptoService::decryptTransaction)
                .toList();
    }
    
    @Override
    public CategoryVariance getCategoryVarianceByBankAccountIds(List<String> bankAccountIds, String type, 
                                                                 LocalDateTime currentStart, LocalDateTime currentEnd,
                                                                 LocalDateTime previousStart, LocalDateTime previousEnd) {
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return new CategoryVariance(new ArrayList<>());
        }
        
        // Get breakdown for current period
        CategoryBreakdown currentBreakdown = getCategoryBreakdownByBankAccountIds(bankAccountIds, type, currentStart, currentEnd);
        
        // Get breakdown for previous period
        CategoryBreakdown previousBreakdown = getCategoryBreakdownByBankAccountIds(bankAccountIds, type, previousStart, previousEnd);
        
        // Create maps for easy lookup
        Map<String, Double> currentMap = currentBreakdown.items().stream()
                .collect(Collectors.toMap(CategoryItem::category, CategoryItem::amount));
        
        Map<String, Double> previousMap = previousBreakdown.items().stream()
                .collect(Collectors.toMap(CategoryItem::category, CategoryItem::amount));
        
        // Get all categories (union of both periods)
        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(currentMap.keySet());
        allCategories.addAll(previousMap.keySet());
        
        // Calculate variance for each category
        List<CategoryVarianceItem> items = allCategories.stream()
                .map(category -> {
                    Double currentAmount = currentMap.getOrDefault(category, 0.0);
                    Double previousAmount = previousMap.getOrDefault(category, 0.0);
                    Double delta = currentAmount - previousAmount;
                    
                    // Calculate percentage change
                    Double deltaPercentage;
                    if (previousAmount == 0) {
                        deltaPercentage = currentAmount > 0 ? 100.0 : 0.0;
                    } else {
                        deltaPercentage = (delta / previousAmount) * 100.0;
                    }
                    
                    // Determine trend
                    String trend;
                    if (Math.abs(delta) < 0.01) {
                        trend = "flat";
                    } else if (delta > 0) {
                        trend = "up";
                    } else {
                        trend = "down";
                    }
                    
                    return new CategoryVarianceItem(
                            category,
                            currentAmount,
                            previousAmount,
                            delta,
                            deltaPercentage,
                            trend
                    );
                })
                // Sort by absolute delta (highest variance first)
                .sorted((a, b) -> Double.compare(Math.abs(b.delta()), Math.abs(a.delta())))
                .toList();
        
        return new CategoryVariance(items);
    }
    
    @Override
    public ExpenseHeatmap getDailyExpenseHeatmapByBankAccountIds(List<String> bankAccountIds, int year, int month, Double manualDailyLimit) {
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return new ExpenseHeatmap(new ArrayList<>(), 0.0, "empty");
        }
        
        // Calculate daily limit
        Double dailyLimit;
        String limitMode;
        
        if (manualDailyLimit != null && manualDailyLimit > 0) {
            dailyLimit = manualDailyLimit;
            limitMode = "manual";
        } else {
            // Calculate from historical average (last 3 months)
            LocalDateTime threeMonthsAgo = LocalDateTime.of(year, month, 1, 0, 0).minusMonths(3);
            LocalDateTime endOfLastMonth = LocalDateTime.of(year, month, 1, 0, 0).minusDays(1).withHour(23).withMinute(59).withSecond(59);
            
            List<Criteria> historicalCriteria = new ArrayList<>();
            historicalCriteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
            historicalCriteria.add(Criteria.where("transaction_date").gte(threeMonthsAgo).lte(endOfLastMonth));
            historicalCriteria.add(Criteria.where("amount_out").gt(0));
            
            MatchOperation historicalMatch = Aggregation.match(new Criteria().andOperator(historicalCriteria.toArray(new Criteria[0])));
            GroupOperation historicalGroup = Aggregation.group().sum("amount_out").as("totalExpense");
            
            Aggregation historicalAgg = Aggregation.newAggregation(historicalMatch, historicalGroup);
            
            record HistoricalResult(Double totalExpense) {}
            AggregationResults<HistoricalResult> historicalResults = mongoTemplate.aggregate(
                    historicalAgg, "transactions", HistoricalResult.class
            );
            
            Double totalHistorical = historicalResults.getMappedResults().isEmpty() ? 0.0 
                    : historicalResults.getMappedResults().get(0).totalExpense();
            
            // Average per day over 90 days
            dailyLimit = totalHistorical / 90.0;
            limitMode = "historical";
            
            // If historical is 0, use a default
            if (dailyLimit == 0) {
                dailyLimit = 500000.0; // Default 500k VND
                limitMode = "default";
            }
        }
        
        // Get daily expenses for the specified month
        LocalDateTime startOfMonth = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusSeconds(1);
        
        List<Criteria> matchCriteria = new ArrayList<>();
        matchCriteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
        matchCriteria.add(Criteria.where("transaction_date").gte(startOfMonth).lte(endOfMonth));
        matchCriteria.add(Criteria.where("amount_out").gt(0));
        
        MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(matchCriteria.toArray(new Criteria[0])));
        
        // Project to extract date (yyyy-MM-dd) and amount_out
        ProjectionOperation projectStage = Aggregation.project("amount_out")
                .andExpression("dateToString('%Y-%m-%d', transaction_date)").as("dateStr");
        
        // Group by dateStr and sum amounts
        GroupOperation groupStage = Aggregation.group("dateStr")
                .sum("amount_out").as("totalAmount");
        
        SortOperation sortStage = Aggregation.sort(Sort.Direction.ASC, "_id");
        
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                projectStage,
                groupStage,
                sortStage
        );
        
        record DailyAggregationResult(String _id, Double totalAmount) {}
        AggregationResults<DailyAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", DailyAggregationResult.class
        );
        
        Map<String, Double> dailyExpenseMap = results.getMappedResults().stream()
                .collect(Collectors.toMap(DailyAggregationResult::_id, DailyAggregationResult::totalAmount));
        
        // Build complete list of days for the month
        int daysInMonth = startOfMonth.toLocalDate().lengthOfMonth();
        List<DailyExpenseData> days = new ArrayList<>();
        
        for (int day = 1; day <= daysInMonth; day++) {
            String dateStr = String.format("%d-%02d-%02d", year, month, day);
            Double amount = dailyExpenseMap.getOrDefault(dateStr, 0.0);
            int level = calculateExpenseLevel(amount, dailyLimit);
            days.add(new DailyExpenseData(dateStr, amount, level));
        }
        
        return new ExpenseHeatmap(days, dailyLimit, limitMode);
    }

    // ===== REPORTS CHARTS DATA IMPLEMENTATION (Phase 3) =====

    @Override
    public CategoryBreakdown getCategorySummaryWithFilters(
            String userId, String accountId, String bankAccountId,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount) {

        List<Criteria> criteria = new ArrayList<>();

        // User/Account filters - use snake_case field names as stored in MongoDB
        if (userId != null && !userId.isEmpty()) {
            criteria.add(Criteria.where("user_id").is(userId));
        }
        if (accountId != null && !accountId.isEmpty()) {
            criteria.add(Criteria.where("account_id").is(accountId));
        }
        if (bankAccountId != null && !bankAccountId.isEmpty()) {
            criteria.add(Criteria.where("bank_account_id").is(bankAccountId));
        }

        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) dateCriteria.gte(startDate);
            if (endDate != null) dateCriteria.lte(endDate);
            criteria.add(dateCriteria);
        }

        // Text search filter
        if (textSearch != null && !textSearch.isEmpty()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("transaction_content").regex(textSearch, "i"),
                Criteria.where("reference_number").regex(textSearch, "i")
            ));
        }

        // Category filter
        if (category != null && !category.isEmpty()) {
            criteria.add(Criteria.where("category").is(category));
        }

        // Transaction type filter (income/expense or IN/OUT)
        if (transactionType != null && !transactionType.isEmpty()) {
            if ("IN".equalsIgnoreCase(transactionType) || "income".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_in").gt(0));
            } else if ("OUT".equalsIgnoreCase(transactionType) || "expense".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_out").gt(0));
            }
        }

        // Amount range filter
        if (minAmount != null || maxAmount != null) {
            Criteria amountCriteria = new Criteria().orOperator(
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_out").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_out").gte(minAmount) :
                    Criteria.where("amount_out").lte(maxAmount),
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_in").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_in").gte(minAmount) :
                    Criteria.where("amount_in").lte(maxAmount)
            );
            criteria.add(amountCriteria);
        }

        MatchOperation matchOperation = Aggregation.match(
            criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]))
        );

        GroupOperation groupOperation = Aggregation.group("category")
                .sum(ConditionalOperators.when(Criteria.where("amount_out").gt(0))
                        .then("$amount_out")
                        .otherwise(0))
                .as("totalExpense")
                .sum(ConditionalOperators.when(Criteria.where("amount_in").gt(0))
                        .then("$amount_in")
                        .otherwise(0))
                .as("totalIncome");

        ProjectionOperation projectOperation = Aggregation.project()
                .and("_id").as("category")
                .andExpression("totalExpense - totalIncome").as("netAmount")
                .and("totalExpense").as("totalExpense")
                .and("totalIncome").as("totalIncome");

        SortOperation sortOperation = Aggregation.sort(Sort.by(Sort.Direction.DESC, "netAmount"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchOperation,
                groupOperation,
                projectOperation,
                sortOperation
        );

        record CategoryAggregationResult(String category, Double netAmount, Double totalExpense, Double totalIncome) {}

        AggregationResults<CategoryAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", CategoryAggregationResult.class);

        List<CategoryAggregationResult> categoryResults = results.getMappedResults();

        // Calculate total for percentage
        double totalAmount = categoryResults.stream()
                .mapToDouble(r -> Math.abs(r.netAmount()))
                .sum();

        List<CategoryItem> items = categoryResults.stream()
                .map(r -> new CategoryItem(
                        r.category() != null && !r.category().isEmpty() ? r.category() : "Uncategorized",
                        Math.abs(r.netAmount()),
                        totalAmount > 0 ? (Math.abs(r.netAmount()) / totalAmount) * 100 : 0
                ))
                .collect(Collectors.toList());

        return new CategoryBreakdown(totalAmount, items);
    }

    @Override
    public CategoryBreakdown getCategorySummaryWithFiltersByBankAccountIds(
            List<String> bankAccountIds,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount) {

        List<Criteria> criteria = new ArrayList<>();

        // Bank account IDs filter - use snake_case field names
        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            criteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
        }

        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) dateCriteria.gte(startDate);
            if (endDate != null) dateCriteria.lte(endDate);
            criteria.add(dateCriteria);
        }

        // Text search filter
        if (textSearch != null && !textSearch.isEmpty()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("transaction_content").regex(textSearch, "i"),
                Criteria.where("reference_number").regex(textSearch, "i")
            ));
        }

        // Category filter
        if (category != null && !category.isEmpty()) {
            criteria.add(Criteria.where("category").is(category));
        }

        // Transaction type filter (income/expense or IN/OUT)
        if (transactionType != null && !transactionType.isEmpty()) {
            if ("IN".equalsIgnoreCase(transactionType) || "income".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_in").gt(0));
            } else if ("OUT".equalsIgnoreCase(transactionType) || "expense".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_out").gt(0));
            }
        }

        // Amount range filter
        if (minAmount != null || maxAmount != null) {
            Criteria amountCriteria = new Criteria().orOperator(
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_out").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_out").gte(minAmount) :
                    Criteria.where("amount_out").lte(maxAmount),
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_in").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_in").gte(minAmount) :
                    Criteria.where("amount_in").lte(maxAmount)
            );
            criteria.add(amountCriteria);
        }

        MatchOperation matchOperation = Aggregation.match(
            criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]))
        );

        GroupOperation groupOperation = Aggregation.group("category")
                .sum(ConditionalOperators.when(Criteria.where("amount_out").gt(0))
                        .then("$amount_out")
                        .otherwise(0))
                .as("totalExpense")
                .sum(ConditionalOperators.when(Criteria.where("amount_in").gt(0))
                        .then("$amount_in")
                        .otherwise(0))
                .as("totalIncome");

        ProjectionOperation projectOperation = Aggregation.project()
                .and("_id").as("category")
                .andExpression("totalExpense - totalIncome").as("netAmount")
                .and("totalExpense").as("totalExpense")
                .and("totalIncome").as("totalIncome");

        SortOperation sortOperation = Aggregation.sort(Sort.by(Sort.Direction.DESC, "netAmount"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchOperation,
                groupOperation,
                projectOperation,
                sortOperation
        );

        record CategoryAggregationResult(String category, Double netAmount, Double totalExpense, Double totalIncome) {}

        AggregationResults<CategoryAggregationResult> results = mongoTemplate.aggregate(
                aggregation, "transactions", CategoryAggregationResult.class);

        List<CategoryAggregationResult> categoryResults = results.getMappedResults();

        double totalAmount = categoryResults.stream()
                .mapToDouble(r -> Math.abs(r.netAmount()))
                .sum();

        List<CategoryItem> items = categoryResults.stream()
                .map(r -> new CategoryItem(
                        r.category() != null && !r.category().isEmpty() ? r.category() : "Uncategorized",
                        Math.abs(r.netAmount()),
                        totalAmount > 0 ? (Math.abs(r.netAmount()) / totalAmount) * 100 : 0
                ))
                .collect(Collectors.toList());

        return new CategoryBreakdown(totalAmount, items);
    }

    @Override
    public List<DailyTrendData> getDailyTrend(
            String userId, String accountId, String bankAccountId,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount) {

        List<Criteria> criteria = new ArrayList<>();

        // User/Account filters - use snake_case field names
        if (userId != null && !userId.isEmpty()) {
            criteria.add(Criteria.where("user_id").is(userId));
        }
        if (accountId != null && !accountId.isEmpty()) {
            criteria.add(Criteria.where("account_id").is(accountId));
        }
        if (bankAccountId != null && !bankAccountId.isEmpty()) {
            criteria.add(Criteria.where("bank_account_id").is(bankAccountId));
        }

        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) dateCriteria.gte(startDate);
            if (endDate != null) dateCriteria.lte(endDate);
            criteria.add(dateCriteria);
        }

        // Text search filter
        if (textSearch != null && !textSearch.isEmpty()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("transaction_content").regex(textSearch, "i"),
                Criteria.where("reference_number").regex(textSearch, "i")
            ));
        }

        // Category filter
        if (category != null && !category.isEmpty()) {
            criteria.add(Criteria.where("category").is(category));
        }

        // Transaction type filter (income/expense or IN/OUT)
        if (transactionType != null && !transactionType.isEmpty()) {
            if ("IN".equalsIgnoreCase(transactionType) || "income".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_in").gt(0));
            } else if ("OUT".equalsIgnoreCase(transactionType) || "expense".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_out").gt(0));
            }
        }

        // Amount range filter
        if (minAmount != null || maxAmount != null) {
            Criteria amountCriteria = new Criteria().orOperator(
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_out").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_out").gte(minAmount) :
                    Criteria.where("amount_out").lte(maxAmount),
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_in").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_in").gte(minAmount) :
                    Criteria.where("amount_in").lte(maxAmount)
            );
            criteria.add(amountCriteria);
        }

        MatchOperation matchOperation = Aggregation.match(
            criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]))
        );

        ProjectionOperation dateProjection = Aggregation.project()
                .and("transaction_date").extractYear().as("year")
                .and("transaction_date").extractMonth().as("month")
                .and("transaction_date").extractDayOfMonth().as("day")
                .and("amount_in").as("amountIn")
                .and("amount_out").as("amountOut");

        GroupOperation groupByDate = Aggregation.group("year", "month", "day")
                .sum("amountIn").as("totalIncome")
                .sum("amountOut").as("totalExpense");

        ProjectionOperation formatDate = Aggregation.project()
                .andExpression("concat(toString(_id.year), '-', " +
                        "cond(lt(_id.month, 10), concat('0', toString(_id.month)), toString(_id.month)), '-', " +
                        "cond(lt(_id.day, 10), concat('0', toString(_id.day)), toString(_id.day)))")
                .as("date")
                .and("totalIncome").as("income")
                .and("totalExpense").as("expense");

        SortOperation sortByDate = Aggregation.sort(Sort.by(Sort.Direction.ASC, "date"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchOperation,
                dateProjection,
                groupByDate,
                formatDate,
                sortByDate
        );

        AggregationResults<DailyTrendData> results = mongoTemplate.aggregate(
                aggregation, "transactions", DailyTrendData.class);

        return results.getMappedResults();
    }

    @Override
    public List<DailyTrendData> getDailyTrendByBankAccountIds(
            List<String> bankAccountIds,
            LocalDateTime startDate, LocalDateTime endDate,
            String textSearch, String category, String transactionType,
            Double minAmount, Double maxAmount) {

        List<Criteria> criteria = new ArrayList<>();

        // Bank account IDs filter - use snake_case field names
        if (bankAccountIds != null && !bankAccountIds.isEmpty()) {
            criteria.add(Criteria.where("bank_account_id").in(bankAccountIds));
        }

        // Date range filter
        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("transaction_date");
            if (startDate != null) dateCriteria.gte(startDate);
            if (endDate != null) dateCriteria.lte(endDate);
            criteria.add(dateCriteria);
        }

        // Text search filter
        if (textSearch != null && !textSearch.isEmpty()) {
            criteria.add(new Criteria().orOperator(
                Criteria.where("transaction_content").regex(textSearch, "i"),
                Criteria.where("reference_number").regex(textSearch, "i")
            ));
        }

        // Category filter
        if (category != null && !category.isEmpty()) {
            criteria.add(Criteria.where("category").is(category));
        }

        // Transaction type filter (income/expense or IN/OUT)
        if (transactionType != null && !transactionType.isEmpty()) {
            if ("IN".equalsIgnoreCase(transactionType) || "income".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_in").gt(0));
            } else if ("OUT".equalsIgnoreCase(transactionType) || "expense".equalsIgnoreCase(transactionType)) {
                criteria.add(Criteria.where("amount_out").gt(0));
            }
        }

        // Amount range filter
        if (minAmount != null || maxAmount != null) {
            Criteria amountCriteria = new Criteria().orOperator(
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_out").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_out").gte(minAmount) :
                    Criteria.where("amount_out").lte(maxAmount),
                minAmount != null && maxAmount != null ?
                    Criteria.where("amount_in").gte(minAmount).lte(maxAmount) :
                    minAmount != null ? Criteria.where("amount_in").gte(minAmount) :
                    Criteria.where("amount_in").lte(maxAmount)
            );
            criteria.add(amountCriteria);
        }

        MatchOperation matchOperation = Aggregation.match(
            criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria.toArray(new Criteria[0]))
        );

        ProjectionOperation dateProjection = Aggregation.project()
                .and("transaction_date").extractYear().as("year")
                .and("transaction_date").extractMonth().as("month")
                .and("transaction_date").extractDayOfMonth().as("day")
                .and("amount_in").as("amountIn")
                .and("amount_out").as("amountOut");

        GroupOperation groupByDate = Aggregation.group("year", "month", "day")
                .sum("amountIn").as("totalIncome")
                .sum("amountOut").as("totalExpense");

        ProjectionOperation formatDate = Aggregation.project()
                .andExpression("concat(toString(_id.year), '-', " +
                        "cond(lt(_id.month, 10), concat('0', toString(_id.month)), toString(_id.month)), '-', " +
                        "cond(lt(_id.day, 10), concat('0', toString(_id.day)), toString(_id.day)))")
                .as("date")
                .and("totalIncome").as("income")
                .and("totalExpense").as("expense");

        SortOperation sortByDate = Aggregation.sort(Sort.by(Sort.Direction.ASC, "date"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchOperation,
                dateProjection,
                groupByDate,
                formatDate,
                sortByDate
        );

        AggregationResults<DailyTrendData> results = mongoTemplate.aggregate(
                aggregation, "transactions", DailyTrendData.class);

        return results.getMappedResults();
    }
}

