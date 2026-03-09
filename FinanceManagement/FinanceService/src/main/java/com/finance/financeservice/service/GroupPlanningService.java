package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.GroupPlanning;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GroupPlanningService {
    GroupPlanning create(GroupPlanning groupPlanning);
    
    Optional<GroupPlanning> getById(String id);
    
    List<GroupPlanning> findByGroup(Long groupId);
    
    GroupPlanning update(String id, GroupPlanning update);
    
    void delete(String id);
    
    void deleteByGroup(Long groupId);

    // Business logic helpers
    void addExpense(Long groupId, String category, double amount, LocalDate when);
    
    void moveExpense(Long groupId, String fromCategory, String toCategory, double amount, LocalDate when);
    
    // Summary methods
    GroupPlanningSummary getSummary(Long groupId);
    
    // Recalculate spent amount from transactions
    void recalculateSpentAmount(String planningId, List<String> bankAccountIds);
    
    void recalculateAllSpentAmounts(Long groupId, List<String> bankAccountIds);
    
    // Recalculate spent amounts with custom date range filter
    void recalculateAllSpentAmountsWithDateRange(Long groupId, List<String> bankAccountIds, String startDate, String endDate);
    
    class GroupPlanningSummary {
        private long totalPlannings;
        private double totalBudgetAmount;
        private double totalSpentAmount;
        private double totalRemainingAmount;
        private double overallProgressPercentage;
        
        // Getters and setters
        public long getTotalPlannings() {
            return totalPlannings;
        }
        
        public void setTotalPlannings(long totalPlannings) {
            this.totalPlannings = totalPlannings;
        }
        
        public double getTotalBudgetAmount() {
            return totalBudgetAmount;
        }
        
        public void setTotalBudgetAmount(double totalBudgetAmount) {
            this.totalBudgetAmount = totalBudgetAmount;
        }
        
        public double getTotalSpentAmount() {
            return totalSpentAmount;
        }
        
        public void setTotalSpentAmount(double totalSpentAmount) {
            this.totalSpentAmount = totalSpentAmount;
        }
        
        public double getTotalRemainingAmount() {
            return totalRemainingAmount;
        }
        
        public void setTotalRemainingAmount(double totalRemainingAmount) {
            this.totalRemainingAmount = totalRemainingAmount;
        }
        
        public double getOverallProgressPercentage() {
            return overallProgressPercentage;
        }
        
        public void setOverallProgressPercentage(double overallProgressPercentage) {
            this.overallProgressPercentage = overallProgressPercentage;
        }
    }
}

