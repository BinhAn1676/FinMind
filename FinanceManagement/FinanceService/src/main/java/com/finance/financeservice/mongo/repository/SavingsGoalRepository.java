package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.SavingsGoal;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavingsGoalRepository extends MongoRepository<SavingsGoal, String> {
    List<SavingsGoal> findByUserId(String userId);
    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(String userId);
    List<SavingsGoal> findByAutoSaveEnabledTrueAndStatus(SavingsGoal.GoalStatus status);
    List<SavingsGoal> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
