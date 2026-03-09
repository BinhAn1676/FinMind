package com.finance.financeservice.scheduler;

import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.service.SepaySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountsSyncJob implements Job {

    private final SepaySyncService sepaySyncService;
    private final SepayOAuth2Properties oauth2Properties;

    @Override
    public void execute(JobExecutionContext context) {
        if (oauth2Properties.isEnabled()) {
            log.info("Executing AccountsSyncJob (OAuth2 mode)");
            sepaySyncService.syncAllAccountsOAuth2();
        } else {
            log.info("Executing AccountsSyncJob (Legacy mode)");
            sepaySyncService.syncAllAccounts();
        }
    }
}


