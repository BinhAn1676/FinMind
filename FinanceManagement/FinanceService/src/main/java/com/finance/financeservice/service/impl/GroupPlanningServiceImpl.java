package com.finance.financeservice.service.impl;

import com.finance.financeservice.exception.ResourceNotFoundException;
import com.finance.financeservice.mongo.document.GroupPlanning;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.GroupPlanningRepository;
import com.finance.financeservice.service.GroupPlanningService;
import com.finance.financeservice.service.client.GroupActivityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPlanningServiceImpl implements GroupPlanningService {

    private final GroupPlanningRepository repository;
    private final GroupActivityClient groupActivityClient;
    private final MongoTemplate mongoTemplate;

    @Override
    public GroupPlanning create(GroupPlanning groupPlanning) {
        if (groupPlanning.getSpentAmount() == null) {
            groupPlanning.setSpentAmount(0.0);
        }
        GroupPlanning saved = repository.save(groupPlanning);
        
        // Log activity
        logPlanningActivity(
                saved.getGroupId(),
                saved.getCreatedBy(),
                "PLANNING_CREATED",
                String.format("Đã tạo kế hoạch '%s' với ngân sách %,.0f₫", 
                        saved.getCategory(), 
                        saved.getBudgetAmount()),
                Map.of(
                        "planningId", saved.getId(),
                        "category", saved.getCategory(),
                        "budgetAmount", saved.getBudgetAmount(),
                        "planType", saved.getPlanType().name()
                )
        );
        
        return saved;
    }

    @Override
    public Optional<GroupPlanning> getById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<GroupPlanning> findByGroup(Long groupId) {
        return repository.findByGroupId(groupId);
    }

    @Override
    public GroupPlanning update(String id, GroupPlanning update) {
        return repository.findById(id).map(existing -> {
            String oldCategory = existing.getCategory();
            Double oldBudget = existing.getBudgetAmount();
            
            if (!ObjectUtils.isEmpty(update.getCategory())) existing.setCategory(update.getCategory());
            if (update.getBudgetAmount() != null) existing.setBudgetAmount(update.getBudgetAmount());
            if (update.getPlanType() != null) existing.setPlanType(update.getPlanType());
            if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
            if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());
            if (update.getRepeatCycle() != null) existing.setRepeatCycle(update.getRepeatCycle());
            if (update.getDayOfMonth() != null) existing.setDayOfMonth(update.getDayOfMonth());
            if (!ObjectUtils.isEmpty(update.getDescription())) existing.setDescription(update.getDescription());
            
            GroupPlanning saved = repository.save(existing);
            
            // Log activity
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planningId", saved.getId());
            metadata.put("category", saved.getCategory());
            metadata.put("budgetAmount", saved.getBudgetAmount());
            if (!ObjectUtils.isEmpty(oldCategory) && !oldCategory.equals(saved.getCategory())) {
                metadata.put("oldCategory", oldCategory);
            }
            if (oldBudget != null && !oldBudget.equals(saved.getBudgetAmount())) {
                metadata.put("oldBudgetAmount", oldBudget);
            }
            
            logPlanningActivity(
                    saved.getGroupId(),
                    saved.getCreatedBy(),
                    "PLANNING_UPDATED",
                    String.format("Đã cập nhật kế hoạch '%s'", saved.getCategory()),
                    metadata
            );
            
            return saved;
        }).orElseThrow(() -> new ResourceNotFoundException("GroupPlanning", "id", id));
    }

    @Override
    public void delete(String id) {
        Optional<GroupPlanning> planningOpt = repository.findById(id);
        if (planningOpt.isPresent()) {
            GroupPlanning planning = planningOpt.get();
            repository.deleteById(id);
            
            // Log activity
            logPlanningActivity(
                    planning.getGroupId(),
                    planning.getCreatedBy(),
                    "PLANNING_DELETED",
                    String.format("Đã xóa kế hoạch '%s'", planning.getCategory()),
                    Map.of(
                            "planningId", planning.getId(),
                            "category", planning.getCategory(),
                            "budgetAmount", planning.getBudgetAmount()
                    )
            );
        } else {
            repository.deleteById(id);
        }
    }

    @Override
    public void deleteByGroup(Long groupId) {
        repository.deleteByGroupId(groupId);
    }

    @Override
    public void addExpense(Long groupId, String category, double amount, LocalDate when) {
        if (amount <= 0) return;
        GroupPlanning target = findApplicable(groupId, category, when);
        if (target == null) return;
        double spent = target.getSpentAmount() != null ? target.getSpentAmount() : 0.0;
        target.setSpentAmount(spent + amount);
        repository.save(target);
    }

    @Override
    public void moveExpense(Long groupId, String fromCategory, String toCategory, double amount, LocalDate when) {
        if (amount <= 0) return;
        if (!ObjectUtils.isEmpty(fromCategory)) {
            GroupPlanning from = findApplicable(groupId, fromCategory, when);
            if (from != null) {
                double spent = from.getSpentAmount() != null ? from.getSpentAmount() : 0.0;
                from.setSpentAmount(Math.max(0.0, spent - amount));
                repository.save(from);
            }
        }
        if (!ObjectUtils.isEmpty(toCategory)) {
            addExpense(groupId, toCategory, amount, when);
        }
    }

    @Override
    public GroupPlanningSummary getSummary(Long groupId) {
        List<GroupPlanning> plannings = repository.findByGroupId(groupId);
        
        GroupPlanningSummary summary = new GroupPlanningSummary();
        summary.setTotalPlannings(plannings.size());
        
        double totalBudget = 0.0;
        double totalSpent = 0.0;
        
        for (GroupPlanning planning : plannings) {
            if (planning.getBudgetAmount() != null) {
                totalBudget += planning.getBudgetAmount();
            }
            if (planning.getSpentAmount() != null) {
                totalSpent += planning.getSpentAmount();
            }
        }
        
        summary.setTotalBudgetAmount(totalBudget);
        summary.setTotalSpentAmount(totalSpent);
        summary.setTotalRemainingAmount(totalBudget - totalSpent);
        
        if (totalBudget > 0) {
            summary.setOverallProgressPercentage((totalSpent / totalBudget) * 100.0);
        } else {
            summary.setOverallProgressPercentage(0.0);
        }
        
        return summary;
    }

    private GroupPlanning findApplicable(Long groupId, String category, LocalDate when) {
        // First, try to find SHORT_TERM or LONG_TERM plans with date range
        List<GroupPlanning> dateBasedPlans = repository
                .findByGroupIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(groupId, when, when);
        GroupPlanning dateBased = dateBasedPlans.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> p.getPlanType() == GroupPlanning.PlanType.SHORT_TERM 
                        || p.getPlanType() == GroupPlanning.PlanType.LONG_TERM)
                .max(Comparator.comparing(GroupPlanning::getStartDate))
                .orElse(null);
        
        if (dateBased != null) {
            return dateBased;
        }
        
        // If no date-based plan found, try RECURRING plans
        List<GroupPlanning> recurringPlans = repository
                .findByGroupIdAndCategoryAndPlanType(groupId, category, GroupPlanning.PlanType.RECURRING);
        
        return recurringPlans.stream()
                .filter(p -> isInRecurringCycle(p, when))
                .findFirst()
                .orElse(null);
    }
    
    private boolean isInRecurringCycle(GroupPlanning plan, LocalDate when) {
        if (plan.getRepeatCycle() == null) {
            return false;
        }
        
        switch (plan.getRepeatCycle()) {
            case MONTHLY:
                // For monthly, if dayOfMonth is null, apply for whole month (always true)
                if (plan.getDayOfMonth() == null) {
                    return true; // Apply for whole month
                }
                // For monthly with specific day, if planDay = 31, use last day of current month
                int currentDay = when.getDayOfMonth();
                int planDay = plan.getDayOfMonth();
                int effectiveDay = planDay;
                if (planDay == 31) {
                    effectiveDay = when.lengthOfMonth(); // Last day of current month
                }
                // Check if we're past the reset day this month
                return currentDay >= effectiveDay;
            case QUARTERLY:
                // For quarterly, dayOfMonth is required
                if (plan.getDayOfMonth() == null) {
                    return false;
                }
                // For quarterly, check if we're in the quarter and past the day
                int quarter = (when.getMonthValue() - 1) / 3;
                int quarterStartMonth = quarter * 3 + 1;
                // If planDay = 31, use last day of quarter start month
                int quarterDay = plan.getDayOfMonth();
                if (quarterDay == 31) {
                    LocalDate quarterStartDate = LocalDate.of(when.getYear(), quarterStartMonth, 1);
                    quarterDay = quarterStartDate.lengthOfMonth();
                }
                LocalDate quarterStart = LocalDate.of(when.getYear(), quarterStartMonth, quarterDay);
                return !when.isBefore(quarterStart);
            case YEARLY:
                // For yearly, if dayOfMonth is null, apply for whole year (always true)
                if (plan.getDayOfMonth() == null) {
                    return true; // Apply for whole year
                }
                // For yearly with specific day, if planDay = 31, use last day of January (31)
                int yearDay = plan.getDayOfMonth();
                if (yearDay == 31) {
                    yearDay = 31; // January always has 31 days
                }
                LocalDate yearStart = LocalDate.of(when.getYear(), 1, yearDay);
                return !when.isBefore(yearStart);
            default:
                return false;
        }
    }

    /**
     * Helper method to log group activities
     * Fails silently if the activity logging service is unavailable
     */
    private void logPlanningActivity(Long groupId, String actorUserId, String activityType, 
                                     String message, Map<String, Object> metadata) {
        if (groupId == null) {
            return;
        }
        
        try {
            GroupActivityClient.LogActivityRequest request = new GroupActivityClient.LogActivityRequest();
            request.setGroupId(groupId);
            
            // Try to parse userId from createdBy (could be String or Long)
            Long actorId = null;
            if (!ObjectUtils.isEmpty(actorUserId)) {
                try {
                    actorId = Long.parseLong(actorUserId);
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse actorUserId '{}' as Long, skipping activity log", actorUserId);
                    return;
                }
            }
            request.setActorUserId(actorId);
            request.setType(activityType);
            request.setMessage(message);
            request.setMetadata(metadata);
            
            groupActivityClient.logActivity(groupId, request);
        } catch (Exception e) {
            // Fail silently - activity logging is not critical
            log.warn("Failed to log group activity for group {}: {}", groupId, e.getMessage());
        }
    }

    @Override
    public void recalculateSpentAmount(String planningId, List<String> bankAccountIds) {
        Optional<GroupPlanning> planningOpt = repository.findById(planningId);
        if (planningOpt.isEmpty()) {
            throw new ResourceNotFoundException("GroupPlanning", "id", planningId);
        }
        
        GroupPlanning planning = planningOpt.get();
        double spentAmount = calculateSpentAmountFromTransactionsWithCycle(
                planning.getGroupId(),
                planning.getCategory(),
                planning.getStartDate(),
                planning.getEndDate(),
                planning.getPlanType(),
                planning.getRepeatCycle(),
                planning.getDayOfMonth(),
                bankAccountIds
        );
        
        planning.setSpentAmount(spentAmount);
        repository.save(planning);
        
        log.info("Recalculated spent amount for planning {}: {}₫", planningId, spentAmount);
    }

    @Override
    public void recalculateAllSpentAmounts(Long groupId, List<String> bankAccountIds) {
        List<GroupPlanning> plannings = repository.findByGroupId(groupId);
        
        log.info("Recalculating spent amounts for {} plannings in group {} with {} bank accounts", 
                plannings.size(), groupId, bankAccountIds.size());
        
        for (GroupPlanning planning : plannings) {
            double spentAmount = calculateSpentAmountFromTransactionsWithCycle(
                    planning.getGroupId(),
                    planning.getCategory(),
                    planning.getStartDate(),
                    planning.getEndDate(),
                    planning.getPlanType(),
                    planning.getRepeatCycle(),
                    planning.getDayOfMonth(),
                    bankAccountIds
            );
            
            log.info("Planning '{}' (id: {}): calculated spent amount = {}₫ (was {}₫)", 
                    planning.getCategory(), planning.getId(), spentAmount, planning.getSpentAmount());
            
            planning.setSpentAmount(spentAmount);
            repository.save(planning);
        }
        
        log.info("Recalculated spent amounts for {} plannings in group {}", plannings.size(), groupId);
    }

    @Override
    public void recalculateAllSpentAmountsWithDateRange(Long groupId, List<String> bankAccountIds, String startDateStr, String endDateStr) {
        List<GroupPlanning> plannings = repository.findByGroupId(groupId);
        
        log.info("Recalculating spent amounts with date range {} to {} for {} plannings in group {} with {} bank accounts", 
                startDateStr, endDateStr, plannings.size(), groupId, bankAccountIds.size());
        
        LocalDate filterStartDate = null;
        LocalDate filterEndDate = null;
        try {
            if (startDateStr != null && !startDateStr.isEmpty()) {
                filterStartDate = LocalDate.parse(startDateStr);
            }
            if (endDateStr != null && !endDateStr.isEmpty()) {
                filterEndDate = LocalDate.parse(endDateStr);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date range, falling back to default recalculation: {}", e.getMessage());
            recalculateAllSpentAmounts(groupId, bankAccountIds);
            return;
        }
        
        for (GroupPlanning planning : plannings) {
            // Only process plans that started before or during the filter period
            if (planning.getStartDate() != null && filterEndDate != null && planning.getStartDate().isAfter(filterEndDate)) {
                // Plan starts after the filter period, set spent to 0
                planning.setSpentAmount(0.0);
                repository.save(planning);
                continue;
            }
            
            double spentAmount;
            if (planning.getPlanType() == GroupPlanning.PlanType.RECURRING) {
                // For RECURRING plans, use cycle-based calculation
                // The cycle dates will be calculated automatically based on repeatCycle and dayOfMonth
                spentAmount = calculateSpentAmountFromTransactionsWithCycle(
                        planning.getGroupId(),
                        planning.getCategory(),
                        filterStartDate,  // Will be used as hint but cycle dates take precedence
                        filterEndDate,
                        planning.getPlanType(),
                        planning.getRepeatCycle(),
                        planning.getDayOfMonth(),
                        bankAccountIds
                );
            } else {
                // For SHORT_TERM and LONG_TERM, use the filter date range
                LocalDate effectiveStartDate = filterStartDate != null ? filterStartDate : planning.getStartDate();
                LocalDate effectiveEndDate = filterEndDate != null ? filterEndDate : planning.getEndDate();
                
                spentAmount = calculateSpentAmountFromTransactionsWithCycle(
                        planning.getGroupId(),
                        planning.getCategory(),
                        effectiveStartDate,
                        effectiveEndDate,
                        planning.getPlanType(),
                        planning.getRepeatCycle(),
                        planning.getDayOfMonth(),
                        bankAccountIds
                );
            }
            
            log.info("Planning '{}' (id: {}, type: {}): calculated spent amount = {}₫ (was {}₫)", 
                    planning.getCategory(), planning.getId(), planning.getPlanType(), spentAmount, planning.getSpentAmount());
            
            planning.setSpentAmount(spentAmount);
            repository.save(planning);
        }
        
        log.info("Recalculated spent amounts with date range for {} plannings in group {}", plannings.size(), groupId);
    }

    /**
     * Calculate the current cycle date range for a RECURRING plan
     * @param repeatCycle The repeat cycle (MONTHLY, QUARTERLY, YEARLY)
     * @param dayOfMonth The day of month when the cycle resets (1-31)
     * @param referenceDate The date to calculate the current cycle for (usually today)
     * @return Array with [startDate, endDate] of the current cycle
     */
    private LocalDate[] calculateCurrentCycleDates(GroupPlanning.RepeatCycle repeatCycle, Integer dayOfMonth, LocalDate referenceDate) {
        if (referenceDate == null) {
            referenceDate = LocalDate.now();
        }
        
        int effectiveDayOfMonth = (dayOfMonth != null && dayOfMonth >= 1 && dayOfMonth <= 31) ? dayOfMonth : 1;
        
        LocalDate cycleStart;
        LocalDate cycleEnd;
        
        if (repeatCycle == GroupPlanning.RepeatCycle.MONTHLY) {
            // For monthly cycle, calculate based on day of month
            int currentDayOfMonth = referenceDate.getDayOfMonth();
            int maxDayInMonth = referenceDate.lengthOfMonth();
            int actualDayOfMonth = Math.min(effectiveDayOfMonth, maxDayInMonth);
            
            if (currentDayOfMonth >= actualDayOfMonth) {
                // We're in the current cycle (started this month)
                cycleStart = referenceDate.withDayOfMonth(Math.min(effectiveDayOfMonth, referenceDate.lengthOfMonth()));
                LocalDate nextMonth = referenceDate.plusMonths(1);
                cycleEnd = nextMonth.withDayOfMonth(Math.min(effectiveDayOfMonth, nextMonth.lengthOfMonth())).minusDays(1);
            } else {
                // We're in the previous cycle (started last month)
                LocalDate prevMonth = referenceDate.minusMonths(1);
                cycleStart = prevMonth.withDayOfMonth(Math.min(effectiveDayOfMonth, prevMonth.lengthOfMonth()));
                cycleEnd = referenceDate.withDayOfMonth(Math.min(effectiveDayOfMonth, referenceDate.lengthOfMonth())).minusDays(1);
            }
        } else if (repeatCycle == GroupPlanning.RepeatCycle.QUARTERLY) {
            // For quarterly cycle, calculate based on quarter
            int currentQuarter = (referenceDate.getMonthValue() - 1) / 3;
            int quarterStartMonth = currentQuarter * 3 + 1;
            
            cycleStart = LocalDate.of(referenceDate.getYear(), quarterStartMonth, 1);
            cycleEnd = cycleStart.plusMonths(3).minusDays(1);
        } else if (repeatCycle == GroupPlanning.RepeatCycle.YEARLY) {
            // For yearly cycle, from January 1 to December 31
            cycleStart = LocalDate.of(referenceDate.getYear(), 1, 1);
            cycleEnd = LocalDate.of(referenceDate.getYear(), 12, 31);
        } else {
            // Default: use current month
            cycleStart = referenceDate.withDayOfMonth(1);
            cycleEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
        }
        
        log.debug("Calculated current cycle for {} with dayOfMonth={}: {} to {}", 
                repeatCycle, effectiveDayOfMonth, cycleStart, cycleEnd);
        
        return new LocalDate[] { cycleStart, cycleEnd };
    }

    /**
     * Calculate spent amount from transactions based on category, date range, and plan type
     */
    private double calculateSpentAmountFromTransactions(
            Long groupId,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            GroupPlanning.PlanType planType,
            List<String> bankAccountIds) {
        
        return calculateSpentAmountFromTransactionsWithCycle(
                groupId, category, startDate, endDate, planType, null, null, bankAccountIds);
    }

    /**
     * Calculate spent amount from transactions based on category, date range, plan type, and cycle info
     */
    private double calculateSpentAmountFromTransactionsWithCycle(
            Long groupId,
            String category,
            LocalDate startDate,
            LocalDate endDate,
            GroupPlanning.PlanType planType,
            GroupPlanning.RepeatCycle repeatCycle,
            Integer dayOfMonth,
            List<String> bankAccountIds) {
        
        if (ObjectUtils.isEmpty(category) || bankAccountIds == null || bankAccountIds.isEmpty()) {
            return 0.0;
        }
        
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();
        
        // Filter by bank account IDs
        criteriaList.add(Criteria.where("bank_account_id").in(bankAccountIds));
        
        // Filter by category
        criteriaList.add(Criteria.where("category").is(category));
        
        // Don't filter by transaction type - handle both income and expense
        // Filter by date range based on plan type
        if (planType == GroupPlanning.PlanType.SHORT_TERM || planType == GroupPlanning.PlanType.LONG_TERM) {
            if (startDate != null) {
                criteriaList.add(Criteria.where("transaction_date").gte(startDate.atStartOfDay()));
            }
            if (endDate != null) {
                criteriaList.add(Criteria.where("transaction_date").lte(endDate.atTime(23, 59, 59)));
            }
        } else if (planType == GroupPlanning.PlanType.RECURRING) {
            // For recurring plans, calculate the current cycle date range
            LocalDate[] cycleDates = calculateCurrentCycleDates(repeatCycle, dayOfMonth, LocalDate.now());
            LocalDate cycleStart = cycleDates[0];
            LocalDate cycleEnd = cycleDates[1];
            
            // If explicit startDate/endDate are provided, intersect with cycle dates
            LocalDate effectiveStart = cycleStart;
            LocalDate effectiveEnd = cycleEnd;
            
            if (startDate != null && startDate.isAfter(cycleStart)) {
                effectiveStart = startDate;
            }
            if (endDate != null && endDate.isBefore(cycleEnd)) {
                effectiveEnd = endDate;
            }
            
            log.info("RECURRING plan cycle: {} to {} (effective: {} to {})", 
                    cycleStart, cycleEnd, effectiveStart, effectiveEnd);
            
            criteriaList.add(Criteria.where("transaction_date").gte(effectiveStart.atStartOfDay()));
            criteriaList.add(Criteria.where("transaction_date").lte(effectiveEnd.atTime(23, 59, 59)));
        }
        
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        
        // Query transactions and sum both amountIn (income) and amountOut (expense)
        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        
        log.debug("Calculating spent amount for group {}, category {}, found {} transactions", 
                groupId, category, transactions.size());
        
        double totalSpent = transactions.stream()
                .mapToDouble(t -> {
                    double amount = 0.0;
                    // Add expense (amountOut)
                    if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                        amount += t.getAmountOut();
                        log.debug("Found expense transaction: {}₫ on {}", t.getAmountOut(), t.getTransactionDate());
                    }
                    // Add income (amountIn) - for income planning like "Lương"
                    if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                        amount += t.getAmountIn();
                        log.debug("Found income transaction: {}₫ on {}", t.getAmountIn(), t.getTransactionDate());
                    }
                    return amount;
                })
                .sum();
        
        log.debug("Total spent amount calculated: {}₫ for category {}", totalSpent, category);
        return totalSpent;
    }
}

