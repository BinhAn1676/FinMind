package com.finance.financeservice.controller;

import com.finance.financeservice.exception.ResourceNotFoundException;
import com.finance.financeservice.mongo.document.GroupPlanning;
import com.finance.financeservice.service.GroupPlanningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/group-plannings")
@RequiredArgsConstructor
public class GroupPlanningController {

    private final GroupPlanningService groupPlanningService;

    @PostMapping
    public ResponseEntity<GroupPlanning> create(@RequestBody GroupPlanning body) {
        if (body.getGroupId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(groupPlanningService.create(body));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupPlanning> get(@PathVariable String id) {
        return groupPlanningService.getById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("GroupPlanning", "id", id));
    }

    @GetMapping
    public ResponseEntity<List<GroupPlanning>> list(@RequestParam("groupId") Long groupId) {
        if (groupId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(groupPlanningService.findByGroup(groupId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupPlanning> update(@PathVariable String id, @RequestBody GroupPlanning body) {
        try {
            return ResponseEntity.ok(groupPlanningService.update(id, body));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("GroupPlanning", "id", id);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        groupPlanningService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/group/{groupId}")
    public ResponseEntity<Void> deleteByGroup(@PathVariable Long groupId) {
        if (groupId == null) {
            return ResponseEntity.badRequest().build();
        }
        groupPlanningService.deleteByGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<GroupPlanningService.GroupPlanningSummary> getSummary(@PathVariable Long groupId) {
        if (groupId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(groupPlanningService.getSummary(groupId));
    }

    @PostMapping("/{id}/recalculate")
    public ResponseEntity<Void> recalculateSpentAmount(
            @PathVariable String id,
            @RequestBody RecalculateRequest request) {
        if (request.getBankAccountIds() == null || request.getBankAccountIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        groupPlanningService.recalculateSpentAmount(id, request.getBankAccountIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/group/{groupId}/recalculate-all")
    public ResponseEntity<Void> recalculateAllSpentAmounts(
            @PathVariable Long groupId,
            @RequestBody RecalculateRequest request) {
        if (groupId == null || request.getBankAccountIds() == null || request.getBankAccountIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // If startDate and endDate are provided, use them for filtering
        if (request.getStartDate() != null && request.getEndDate() != null) {
            groupPlanningService.recalculateAllSpentAmountsWithDateRange(
                groupId, 
                request.getBankAccountIds(),
                request.getStartDate(),
                request.getEndDate()
            );
        } else {
            groupPlanningService.recalculateAllSpentAmounts(groupId, request.getBankAccountIds());
        }
        return ResponseEntity.ok().build();
    }

    @lombok.Data
    public static class RecalculateRequest {
        private java.util.List<String> bankAccountIds;
        private String startDate; // yyyy-MM-dd
        private String endDate;   // yyyy-MM-dd
    }
}

