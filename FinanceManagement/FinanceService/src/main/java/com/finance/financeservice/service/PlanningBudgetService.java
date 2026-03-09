package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.PlanningBudget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlanningBudgetService {
    PlanningBudget create(PlanningBudget planningBudget);
    Optional<PlanningBudget> getById(String id);
    List<PlanningBudget> findByUser(String userId);
    PlanningBudget update(String id, PlanningBudget update);
    void delete(String id);

    // Business logic helpers
    void addExpense(String userId, String category, double amount, LocalDate when);
    void moveExpense(String userId, String fromCategory, String toCategory, double amount, LocalDate when);

    // Recalculate spent amounts from transactions
    void recalculateSpentAmount(String planningId, List<String> bankAccountIds);
    void recalculateAllSpentAmounts(String userId, List<String> bankAccountIds);
    void recalculateAllSpentAmountsWithDateRange(String userId, List<String> bankAccountIds, String startDate, String endDate);
}


