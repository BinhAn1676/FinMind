package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.PlanningBudget;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.PlanningBudgetRepository;
import com.finance.financeservice.service.PlanningBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningBudgetServiceImpl implements PlanningBudgetService {

    private final PlanningBudgetRepository repository;
    private final MongoTemplate mongoTemplate;

    @Override
    public PlanningBudget create(PlanningBudget planningBudget) {
        if (planningBudget.getSpentAmount() == null) {
            planningBudget.setSpentAmount(0.0);
        }
        return repository.save(planningBudget);
    }

    @Override
    public Optional<PlanningBudget> getById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<PlanningBudget> findByUser(String userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public PlanningBudget update(String id, PlanningBudget update) {
        return repository.findById(id).map(existing -> {
            if (!ObjectUtils.isEmpty(update.getCategory())) existing.setCategory(update.getCategory());
            if (update.getBudgetAmount() != null) existing.setBudgetAmount(update.getBudgetAmount());
            if (update.getPlanType() != null) existing.setPlanType(update.getPlanType());
            if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
            if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());
            if (update.getRepeatCycle() != null) existing.setRepeatCycle(update.getRepeatCycle());
            if (update.getDayOfMonth() != null) existing.setDayOfMonth(update.getDayOfMonth());
            if (!ObjectUtils.isEmpty(update.getIcon())) existing.setIcon(update.getIcon());
            if (!ObjectUtils.isEmpty(update.getColor())) existing.setColor(update.getColor());
            return repository.save(existing);
        }).orElseThrow(() -> new IllegalArgumentException("PlanningBudget not found"));
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    @Override
    public void addExpense(String userId, String category, double amount, LocalDate when) {
        if (amount <= 0) return;
        PlanningBudget target = findApplicable(userId, category, when);
        if (target == null) return;
        double spent = target.getSpentAmount() != null ? target.getSpentAmount() : 0.0;
        target.setSpentAmount(spent + amount);
        repository.save(target);
    }

    @Override
    public void moveExpense(String userId, String fromCategory, String toCategory, double amount, LocalDate when) {
        if (amount <= 0) return;
        if (!ObjectUtils.isEmpty(fromCategory)) {
            PlanningBudget from = findApplicable(userId, fromCategory, when);
            if (from != null) {
                double spent = from.getSpentAmount() != null ? from.getSpentAmount() : 0.0;
                from.setSpentAmount(Math.max(0.0, spent - amount));
                repository.save(from);
            }
        }
        if (!ObjectUtils.isEmpty(toCategory)) {
            addExpense(userId, toCategory, amount, when);
        }
    }

    private PlanningBudget findApplicable(String userId, String category, LocalDate when) {
        // First, try to find SHORT_TERM or LONG_TERM plans with date range
        List<PlanningBudget> dateBasedPlans = repository
                .findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(userId, when, when);
        PlanningBudget dateBased = dateBasedPlans.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> p.getPlanType() == PlanningBudget.PlanType.SHORT_TERM 
                        || p.getPlanType() == PlanningBudget.PlanType.LONG_TERM)
                .max(Comparator.comparing(PlanningBudget::getStartDate))
                .orElse(null);
        
        if (dateBased != null) {
            return dateBased;
        }
        
        // If no date-based plan found, try RECURRING plans
        List<PlanningBudget> recurringPlans = repository
                .findByUserIdAndCategoryAndPlanType(userId, category, PlanningBudget.PlanType.RECURRING);
        
        return recurringPlans.stream()
                .filter(p -> isInRecurringCycle(p, when))
                .findFirst()
                .orElse(null);
    }
    
    private boolean isInRecurringCycle(PlanningBudget plan, LocalDate when) {
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

    @Override
    public void recalculateSpentAmount(String planningId, List<String> bankAccountIds) {
        Optional<PlanningBudget> planningOpt = repository.findById(planningId);
        if (planningOpt.isEmpty()) {
            throw new IllegalArgumentException("PlanningBudget not found: " + planningId);
        }
        
        PlanningBudget planning = planningOpt.get();
        double spentAmount = calculateSpentAmountFromTransactionsWithCycle(
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
    public void recalculateAllSpentAmounts(String userId, List<String> bankAccountIds) {
        List<PlanningBudget> plannings = repository.findByUserId(userId);
        
        log.info("Recalculating spent amounts for {} plannings for user {} with {} bank accounts", 
                plannings.size(), userId, bankAccountIds.size());
        
        for (PlanningBudget planning : plannings) {
            double spentAmount = calculateSpentAmountFromTransactionsWithCycle(
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
        
        log.info("Recalculated spent amounts for {} plannings for user {}", plannings.size(), userId);
    }

    @Override
    public void recalculateAllSpentAmountsWithDateRange(String userId, List<String> bankAccountIds, String startDateStr, String endDateStr) {
        List<PlanningBudget> plannings = repository.findByUserId(userId);
        
        log.info("Recalculating spent amounts with date range {} to {} for {} plannings for user {} with {} bank accounts", 
                startDateStr, endDateStr, plannings.size(), userId, bankAccountIds.size());
        
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
            recalculateAllSpentAmounts(userId, bankAccountIds);
            return;
        }
        
        for (PlanningBudget planning : plannings) {
            // Only process plans that started before or during the filter period
            if (planning.getStartDate() != null && filterEndDate != null && planning.getStartDate().isAfter(filterEndDate)) {
                planning.setSpentAmount(0.0);
                repository.save(planning);
                continue;
            }
            
            double spentAmount;
            if (planning.getPlanType() == PlanningBudget.PlanType.RECURRING) {
                // For RECURRING plans, use cycle-based calculation
                spentAmount = calculateSpentAmountFromTransactionsWithCycle(
                        planning.getCategory(),
                        filterStartDate,
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
        
        log.info("Recalculated spent amounts with date range for {} plannings for user {}", plannings.size(), userId);
    }

    /**
     * Calculate the current cycle date range for a RECURRING plan
     */
    private LocalDate[] calculateCurrentCycleDates(PlanningBudget.RepeatCycle repeatCycle, Integer dayOfMonth, LocalDate referenceDate) {
        if (referenceDate == null) {
            referenceDate = LocalDate.now();
        }
        
        int effectiveDayOfMonth = (dayOfMonth != null && dayOfMonth >= 1 && dayOfMonth <= 31) ? dayOfMonth : 1;
        
        LocalDate cycleStart;
        LocalDate cycleEnd;
        
        if (repeatCycle == PlanningBudget.RepeatCycle.MONTHLY) {
            int currentDayOfMonth = referenceDate.getDayOfMonth();
            int maxDayInMonth = referenceDate.lengthOfMonth();
            int actualDayOfMonth = Math.min(effectiveDayOfMonth, maxDayInMonth);
            
            if (currentDayOfMonth >= actualDayOfMonth) {
                cycleStart = referenceDate.withDayOfMonth(Math.min(effectiveDayOfMonth, referenceDate.lengthOfMonth()));
                LocalDate nextMonth = referenceDate.plusMonths(1);
                cycleEnd = nextMonth.withDayOfMonth(Math.min(effectiveDayOfMonth, nextMonth.lengthOfMonth())).minusDays(1);
            } else {
                LocalDate prevMonth = referenceDate.minusMonths(1);
                cycleStart = prevMonth.withDayOfMonth(Math.min(effectiveDayOfMonth, prevMonth.lengthOfMonth()));
                cycleEnd = referenceDate.withDayOfMonth(Math.min(effectiveDayOfMonth, referenceDate.lengthOfMonth())).minusDays(1);
            }
        } else if (repeatCycle == PlanningBudget.RepeatCycle.QUARTERLY) {
            int currentQuarter = (referenceDate.getMonthValue() - 1) / 3;
            int quarterStartMonth = currentQuarter * 3 + 1;
            
            cycleStart = LocalDate.of(referenceDate.getYear(), quarterStartMonth, 1);
            cycleEnd = cycleStart.plusMonths(3).minusDays(1);
        } else if (repeatCycle == PlanningBudget.RepeatCycle.YEARLY) {
            cycleStart = LocalDate.of(referenceDate.getYear(), 1, 1);
            cycleEnd = LocalDate.of(referenceDate.getYear(), 12, 31);
        } else {
            cycleStart = referenceDate.withDayOfMonth(1);
            cycleEnd = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth());
        }
        
        log.debug("Calculated current cycle for {} with dayOfMonth={}: {} to {}", 
                repeatCycle, effectiveDayOfMonth, cycleStart, cycleEnd);
        
        return new LocalDate[] { cycleStart, cycleEnd };
    }

    /**
     * Calculate spent amount from transactions based on category, date range, plan type, and cycle info
     */
    private double calculateSpentAmountFromTransactionsWithCycle(
            String category,
            LocalDate startDate,
            LocalDate endDate,
            PlanningBudget.PlanType planType,
            PlanningBudget.RepeatCycle repeatCycle,
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
        
        // Filter by date range based on plan type
        if (planType == PlanningBudget.PlanType.SHORT_TERM || planType == PlanningBudget.PlanType.LONG_TERM) {
            if (startDate != null) {
                criteriaList.add(Criteria.where("transaction_date").gte(startDate.atStartOfDay()));
            }
            if (endDate != null) {
                criteriaList.add(Criteria.where("transaction_date").lte(endDate.atTime(23, 59, 59)));
            }
        } else if (planType == PlanningBudget.PlanType.RECURRING) {
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
        
        List<Transaction> transactions = mongoTemplate.find(query, Transaction.class);
        
        log.debug("Calculating spent amount for category {}, found {} transactions", 
                category, transactions.size());
        
        double totalSpent = transactions.stream()
                .mapToDouble(t -> {
                    double amount = 0.0;
                    if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                        amount += t.getAmountOut();
                    }
                    if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                        amount += t.getAmountIn();
                    }
                    return amount;
                })
                .sum();
        
        log.debug("Total spent amount calculated: {}₫ for category {}", totalSpent, category);
        return totalSpent;
    }
}


