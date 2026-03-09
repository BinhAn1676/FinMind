package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.PlanningBudget;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlanningBudgetRepository extends MongoRepository<PlanningBudget, String> {
    List<PlanningBudget> findByUserId(String userId);
    List<PlanningBudget> findByUserIdAndCategory(String userId, String category);
    List<PlanningBudget> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String userId, LocalDate date1, LocalDate date2);
    List<PlanningBudget> findByUserIdAndCategoryAndPlanType(String userId, String category, PlanningBudget.PlanType planType);
}


