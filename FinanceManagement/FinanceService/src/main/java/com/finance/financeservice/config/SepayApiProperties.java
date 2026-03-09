package com.finance.financeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sepay.api")
public class SepayApiProperties {
    // Legacy API URLs (used when oauth2.enabled=false)
    private String accountsUrl;
    private String transactionsUrl;
    // OAuth2 API URLs (used when oauth2.enabled=true)
    private String oauth2AccountsUrl;
    private String oauth2TransactionsUrl;
    private String oauth2WebhooksUrl;
    // Quartz cron expressions
    private String accountsCron;
    private String transactionsCron;
}


