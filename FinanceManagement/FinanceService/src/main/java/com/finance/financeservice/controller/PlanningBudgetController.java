package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.PlanningBudget;
import com.finance.financeservice.service.PlanningBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/planning-budgets")
@RequiredArgsConstructor
public class PlanningBudgetController {

    private final PlanningBudgetService planningBudgetService;

    @PostMapping
    public ResponseEntity<PlanningBudget> create(@RequestBody PlanningBudget body) {
        if (body.getUserId() == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(planningBudgetService.create(body));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanningBudget> get(@PathVariable String id) {
        return planningBudgetService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PlanningBudget>> list(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(planningBudgetService.findByUser(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanningBudget> update(@PathVariable String id, @RequestBody PlanningBudget body) {
        return ResponseEntity.ok(planningBudgetService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        planningBudgetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<Void> recalculateSpentAmount(
            @PathVariable String id,
            @RequestBody Map<String, List<String>> body) {
        List<String> bankAccountIds = body.get("bankAccountIds");
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        planningBudgetService.recalculateSpentAmount(id, bankAccountIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recalculate-all")
    public ResponseEntity<Void> recalculateAllSpentAmounts(
            @RequestParam("userId") String userId,
            @RequestBody Map<String, List<String>> body) {
        List<String> bankAccountIds = body.get("bankAccountIds");
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        planningBudgetService.recalculateAllSpentAmounts(userId, bankAccountIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/recalculate-all-with-range")
    public ResponseEntity<Void> recalculateAllSpentAmountsWithDateRange(
            @RequestParam("userId") String userId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestBody Map<String, List<String>> body) {
        List<String> bankAccountIds = body.get("bankAccountIds");
        if (bankAccountIds == null || bankAccountIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        planningBudgetService.recalculateAllSpentAmountsWithDateRange(userId, bankAccountIds, startDate, endDate);
        return ResponseEntity.ok().build();
    }
}


