package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.SavingsGoal;
import com.finance.financeservice.service.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/savings-goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    @GetMapping
    public ResponseEntity<List<SavingsGoal>> findByUserId(@RequestParam String userId) {
        return ResponseEntity.ok(savingsGoalService.findByUserId(userId));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<SavingsGoal>> findByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(savingsGoalService.findByGroupId(groupId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavingsGoal> findById(@PathVariable String id) {
        return savingsGoalService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SavingsGoal> create(@RequestBody SavingsGoal goal) {
        return ResponseEntity.ok(savingsGoalService.create(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoal> update(@PathVariable String id, @RequestBody SavingsGoal goal) {
        try {
            return ResponseEntity.ok(savingsGoalService.update(id, goal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        savingsGoalService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<SavingsGoal> addContribution(
            @PathVariable String id,
            @RequestBody ContributionRequest request) {
        try {
            return ResponseEntity.ok(savingsGoalService.addContribution(id, request.amount(), request.note()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<SavingsGoal> removeContribution(
            @PathVariable String id,
            @PathVariable String contributionId) {
        try {
            return ResponseEntity.ok(savingsGoalService.removeContribution(id, contributionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record ContributionRequest(Double amount, String note) {}
}
