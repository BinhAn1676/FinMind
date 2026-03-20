package com.finance.financeservice.scheduler;

import com.finance.financeservice.mongo.document.SavingsGoal;
import com.finance.financeservice.mongo.repository.SavingsGoalRepository;
import com.finance.financeservice.service.SavingsGoalService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class AutoSaveGoalJob implements Job {

    @Autowired
    private SavingsGoalService savingsGoalService;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Override
    public void execute(JobExecutionContext context) {
        LocalDate today = LocalDate.now();
        log.info("Running AutoSaveGoalJob for date: {}", today);

        List<SavingsGoal> goals = savingsGoalRepository.findByAutoSaveEnabledTrueAndStatus(SavingsGoal.GoalStatus.ACTIVE);
        log.info("Found {} active auto-save goals", goals.size());

        for (SavingsGoal goal : goals) {
            try {
                if (shouldAutoSaveToday(goal, today)) {
                    log.info("Auto-saving {}đ to goal '{}' ({})", goal.getAutoSaveAmount(), goal.getName(), goal.getId());
                    savingsGoalService.addContribution(goal.getId(), goal.getAutoSaveAmount(), "Tự động tiết kiệm");
                    goal.setLastAutoSaveAt(today);
                    savingsGoalRepository.save(goal);
                }
            } catch (Exception e) {
                log.error("Failed to auto-save for goal {}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    private boolean shouldAutoSaveToday(SavingsGoal goal, LocalDate today) {
        if (goal.getAutoSaveCycle() == null || goal.getAutoSaveAmount() == null || goal.getAutoSaveAmount() <= 0) {
            return false;
        }
        if (goal.getLastAutoSaveAt() == null) return true;

        LocalDate last = goal.getLastAutoSaveAt();
        return switch (goal.getAutoSaveCycle()) {
            case DAILY -> last.isBefore(today);
            case WEEKLY -> !last.plusWeeks(1).isAfter(today);
            case MONTHLY -> !last.plusMonths(1).isAfter(today);
        };
    }
}
