package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.SavingsGoal;
import com.finance.financeservice.mongo.repository.SavingsGoalRepository;
import com.finance.financeservice.service.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalServiceImpl implements SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;

    @Override
    public List<SavingsGoal> findByUserId(String userId) {
        return savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<SavingsGoal> findByGroupId(Long groupId) {
        return savingsGoalRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    @Override
    public Optional<SavingsGoal> findById(String id) {
        return savingsGoalRepository.findById(id);
    }

    @Override
    public SavingsGoal create(SavingsGoal goal) {
        if (goal.getCurrentAmount() == null) goal.setCurrentAmount(0.0);
        if (goal.getStatus() == null) goal.setStatus(SavingsGoal.GoalStatus.ACTIVE);
        if (goal.getContributions() == null) goal.setContributions(new ArrayList<>());
        goal.setCreatedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());
        return savingsGoalRepository.save(goal);
    }

    @Override
    public SavingsGoal update(String id, SavingsGoal update) {
        return savingsGoalRepository.findById(id).map(existing -> {
            if (update.getName() != null) existing.setName(update.getName());
            if (update.getDescription() != null) existing.setDescription(update.getDescription());
            if (update.getTargetAmount() != null) existing.setTargetAmount(update.getTargetAmount());
            if (update.getCurrentAmount() != null) existing.setCurrentAmount(update.getCurrentAmount());
            if (update.getTargetDate() != null) existing.setTargetDate(update.getTargetDate());
            if (update.getStatus() != null) existing.setStatus(update.getStatus());
            if (update.getIcon() != null) existing.setIcon(update.getIcon());
            if (update.getColor() != null) existing.setColor(update.getColor());
            if (update.getAutoSaveAmount() != null) existing.setAutoSaveAmount(update.getAutoSaveAmount());
            if (update.getAutoSaveCycle() != null) existing.setAutoSaveCycle(update.getAutoSaveCycle());
            if (update.getAutoSaveEnabled() != null) existing.setAutoSaveEnabled(update.getAutoSaveEnabled());
            existing.setUpdatedAt(LocalDateTime.now());
            return savingsGoalRepository.save(existing);
        }).orElseThrow(() -> new IllegalArgumentException("SavingsGoal not found: " + id));
    }

    @Override
    public void delete(String id) {
        savingsGoalRepository.deleteById(id);
    }

    @Override
    public SavingsGoal addContribution(String id, Double amount, String note) {
        return savingsGoalRepository.findById(id).map(goal -> {
            if (goal.getContributions() == null) goal.setContributions(new ArrayList<>());

            SavingsGoal.Contribution contribution = SavingsGoal.Contribution.builder()
                    .id(UUID.randomUUID().toString())
                    .amount(amount)
                    .note(note)
                    .createdAt(LocalDateTime.now())
                    .build();

            goal.getContributions().add(contribution);

            double newTotal = goal.getContributions().stream()
                    .mapToDouble(c -> c.getAmount() != null ? c.getAmount() : 0.0)
                    .sum();
            goal.setCurrentAmount(newTotal);

            if (goal.getTargetAmount() != null && newTotal >= goal.getTargetAmount()
                    && goal.getStatus() == SavingsGoal.GoalStatus.ACTIVE) {
                goal.setStatus(SavingsGoal.GoalStatus.COMPLETED);
            }

            goal.setUpdatedAt(LocalDateTime.now());
            return savingsGoalRepository.save(goal);
        }).orElseThrow(() -> new IllegalArgumentException("SavingsGoal not found: " + id));
    }

    @Override
    public SavingsGoal removeContribution(String goalId, String contributionId) {
        return savingsGoalRepository.findById(goalId).map(goal -> {
            if (goal.getContributions() != null) {
                goal.getContributions().removeIf(c -> contributionId.equals(c.getId()));
                double newTotal = goal.getContributions().stream()
                        .mapToDouble(c -> c.getAmount() != null ? c.getAmount() : 0.0)
                        .sum();
                goal.setCurrentAmount(newTotal);
                if (goal.getStatus() == SavingsGoal.GoalStatus.COMPLETED
                        && goal.getTargetAmount() != null
                        && newTotal < goal.getTargetAmount()) {
                    goal.setStatus(SavingsGoal.GoalStatus.ACTIVE);
                }
            }
            goal.setUpdatedAt(LocalDateTime.now());
            return savingsGoalRepository.save(goal);
        }).orElseThrow(() -> new IllegalArgumentException("SavingsGoal not found: " + goalId));
    }
}
