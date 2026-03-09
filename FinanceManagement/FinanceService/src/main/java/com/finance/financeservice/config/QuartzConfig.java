package com.finance.financeservice.config;

import com.finance.financeservice.scheduler.AccountsSyncJob;
import com.finance.financeservice.scheduler.TransactionsSyncJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class QuartzConfig {

    private final SepayApiProperties sepayApiProperties;
    private final SepayOAuth2Properties sepayOAuth2Properties;

    /**
     * Account sync job runs regardless of OAuth2 mode.
     * - Legacy mode: syncs using manual bank token via old API
     * - OAuth2 mode: syncs using OAuth2 access token via new API
     */
    @Bean
    public JobDetail accountsJobDetail() {
        return JobBuilder.newJob(AccountsSyncJob.class)
                .withIdentity("accountsSyncJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger accountsJobTrigger(JobDetail accountsJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(accountsJobDetail)
                .withIdentity("accountsSyncTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(sepayApiProperties.getAccountsCron()))
                .build();
    }

    /**
     * Transaction sync job:
     * - Legacy mode (oauth2.enabled=false): runs on cron to poll transactions
     * - OAuth2 mode (oauth2.enabled=true): still registered but transactions are
     *   primarily received via webhooks. The job serves as a fallback to catch
     *   any transactions that might have been missed by webhooks.
     */
    @Bean
    public JobDetail transactionsJobDetail() {
        return JobBuilder.newJob(TransactionsSyncJob.class)
                .withIdentity("transactionsSyncJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger transactionsJobTrigger(JobDetail transactionsJobDetail) {
        if (sepayOAuth2Properties.isEnabled()) {
            log.info("OAuth2 mode enabled: transaction sync job will run less frequently (webhooks are primary)");
            // In OAuth2 mode, run less frequently as a fallback (every 6 hours)
            return TriggerBuilder.newTrigger()
                    .forJob(transactionsJobDetail)
                    .withIdentity("transactionsSyncTrigger")
                    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 */6 * * ?"))
                    .build();
        }
        return TriggerBuilder.newTrigger()
                .forJob(transactionsJobDetail)
                .withIdentity("transactionsSyncTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(sepayApiProperties.getTransactionsCron()))
                .build();
    }
}


