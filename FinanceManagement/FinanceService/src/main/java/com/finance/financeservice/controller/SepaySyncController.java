package com.finance.financeservice.controller;

import com.finance.financeservice.dto.sepay.SyncResult;
import com.finance.financeservice.service.sepay.SepaySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1/sepay")
@RequiredArgsConstructor
public class SepaySyncController {

    private final SepaySyncService sepaySyncService;

    @Autowired
    private Scheduler scheduler;

    @PostMapping("/sync/accounts")
    public ResponseEntity<SyncResult> syncAccounts(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(sepaySyncService.syncAccountsForUserApi(userId));
    }

    @PostMapping("/sync/transactions")
    public ResponseEntity<SyncResult> syncTransactions(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(sepaySyncService.syncTransactionsForUserApi(userId));
    }

    /**
     * Manually trigger a schedule job (accounts or transactions)
     * GET /api/v1/sepay/schedule/trigger?job=accounts|transactions
     */
    @GetMapping("/schedule/trigger")
    public ResponseEntity<String> triggerJob(@RequestParam("job") String job) {
        String jobName;
        if ("accounts".equalsIgnoreCase(job)) {
            jobName = "accountsSyncJob";
        } else if ("transactions".equalsIgnoreCase(job)) {
            jobName = "transactionsSyncJob";
        } else {
            return ResponseEntity.badRequest().body("Unknown job: " + job);
        }
        try {
            scheduler.triggerJob(JobKey.jobKey(jobName));
            return ResponseEntity.ok("Triggered job: " + jobName + " successfully");
        } catch (SchedulerException e) {
            return ResponseEntity.internalServerError().body("Failed to trigger job: " + e.getMessage());
        }
    }
}


