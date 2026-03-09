package com.finance.financeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sepay.oauth2")
public class SepayOAuth2Properties {
    /**
     * Feature flag: false = legacy manual token mode, true = OAuth2 automatic mode.
     * Keep false for local development, set true when deploying to production.
     */
    private boolean enabled = false;

    private String clientId;
    private String clientSecret;
    private String authorizeUrl;
    private String tokenUrl;
    private String redirectUri;
    private String scopes;

    // Webhook configuration
    private String webhookCallbackUrl;
    private String webhookEventType = "All";
    private String webhookAuthType = "No_Authen";
    private String webhookApiKey;
}
