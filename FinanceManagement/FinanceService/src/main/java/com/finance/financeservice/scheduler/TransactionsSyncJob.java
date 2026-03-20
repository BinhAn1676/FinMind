package com.finance.financeservice.scheduler;

import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.service.sepay.SepaySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionsSyncJob implements Job {

    private final SepaySyncService sepaySyncService;
    private final SepayOAuth2Properties oauth2Properties;

    @Override
    public void execute(JobExecutionContext context) {
        if (oauth2Properties.isEnabled()) {
            // In OAuth2 mode, transactions come via webhooks.
            // This job runs as a fallback to catch missed transactions.
            log.info("Executing TransactionsSyncJob (OAuth2 fallback mode)");
            // Use OAuth2 tokens to sync via new API
            sepaySyncService.syncAllAccountsOAuth2(); // also syncs missing transactions
        } else {
            log.info("Executing TransactionsSyncJob (Legacy mode)");
            sepaySyncService.syncAllTransactions();
        }
    }
}


