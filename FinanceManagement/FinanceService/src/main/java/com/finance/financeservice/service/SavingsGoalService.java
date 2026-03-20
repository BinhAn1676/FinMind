package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.SavingsGoal;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalService {

    List<SavingsGoal> findByUserId(String userId);

    List<SavingsGoal> findByGroupId(Long groupId);

    Optional<SavingsGoal> findById(String id);

    SavingsGoal create(SavingsGoal goal);

    SavingsGoal update(String id, SavingsGoal update);

    void delete(String id);

    SavingsGoal addContribution(String id, Double amount, String note);

    SavingsGoal removeContribution(String goalId, String contributionId);
}
