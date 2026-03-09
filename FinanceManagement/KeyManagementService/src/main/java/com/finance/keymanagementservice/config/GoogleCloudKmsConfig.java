package com.finance.keymanagementservice.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.KeyManagementServiceSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.threeten.bp.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@Slf4j
public class GoogleCloudKmsConfig {
    
    @Value("${google.cloud.kms.credentials-file-path:}")
    private String credentialsFilePath;
    
    @Value("${google.cloud.kms.timeout-seconds:120}")
    private int timeoutSeconds;
    
    @Bean
    @ConditionalOnProperty(name = "google.cloud.kms.enabled", havingValue = "true", matchIfMissing = false)
    public KeyManagementServiceClient keyManagementServiceClient() {
        try {
            log.info("Initializing Google Cloud KMS client with timeout: {} seconds", timeoutSeconds);
            
            KeyManagementServiceSettings.Builder settingsBuilder = KeyManagementServiceSettings.newBuilder();
            
            // Configure timeout and retry settings
            Duration timeout = Duration.ofSeconds(timeoutSeconds);
            RetrySettings retrySettings = RetrySettings.newBuilder()
                    .setInitialRetryDelay(Duration.ofMillis(100))
                    .setRetryDelayMultiplier(2.0)
                    .setMaxRetryDelay(Duration.ofSeconds(5))
                    .setInitialRpcTimeout(timeout)
                    .setRpcTimeoutMultiplier(1.0)
                    .setMaxRpcTimeout(timeout)
                    .setTotalTimeout(timeout)
                    .build();
            
            // Apply retry settings to all methods
            settingsBuilder.encryptSettings().setRetrySettings(retrySettings);
            settingsBuilder.decryptSettings().setRetrySettings(retrySettings);
            
            // If credentials file path is provided, use it
            if (credentialsFilePath != null && !credentialsFilePath.isEmpty()) {
                log.info("Using credentials file: {}", credentialsFilePath);
                
                // Check if file exists
                File credentialsFile = new File(credentialsFilePath);
                if (!credentialsFile.exists()) {
                    log.error("Credentials file not found: {}", credentialsFilePath);
                    throw new RuntimeException("Credentials file not found: " + credentialsFilePath);
                }
                
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(credentialsFile)
                );
                settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials));
                log.info("Successfully loaded credentials from file");
            } else {
                log.info("Using Application Default Credentials (ADC)");
                // Will use Application Default Credentials
                // Run: gcloud auth application-default login
            }
            
            KeyManagementServiceClient client = KeyManagementServiceClient.create(settingsBuilder.build());
            log.info("Successfully initialized Google Cloud KMS client with timeout: {} seconds", timeoutSeconds);
            
            return client;
            
        } catch (IOException e) {
            log.error("Failed to initialize Google Cloud KMS client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Google Cloud KMS client", e);
        }
    }
}

