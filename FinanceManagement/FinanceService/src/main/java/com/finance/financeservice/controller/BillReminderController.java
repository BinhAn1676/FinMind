package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.BillReminder;
import com.finance.financeservice.service.BillReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bill-reminders")
@RequiredArgsConstructor
public class BillReminderController {

    private final BillReminderService billReminderService;

    @GetMapping
    public ResponseEntity<List<BillReminder>> findByUserId(@RequestParam String userId) {
        return ResponseEntity.ok(billReminderService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<BillReminder> create(@RequestBody BillReminder bill) {
        return ResponseEntity.ok(billReminderService.create(bill));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BillReminder> update(@PathVariable String id, @RequestBody BillReminder bill) {
        try {
            return ResponseEntity.ok(billReminderService.update(id, bill));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        billReminderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<BillReminder> markAsPaid(@PathVariable String id, @RequestBody(required = false) PayRequest req) {
        try {
            String note = req != null ? req.note() : null;
            return ResponseEntity.ok(billReminderService.markAsPaid(id, note));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/pay")
    public ResponseEntity<BillReminder> unmarkAsPaid(@PathVariable String id) {
        try {
            return ResponseEntity.ok(billReminderService.unmarkAsPaid(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record PayRequest(String note) {}
}
