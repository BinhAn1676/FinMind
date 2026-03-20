package com.finance.financeservice.service.sepay;

import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.dto.keys.DecryptRequest;
import com.finance.financeservice.dto.keys.DecryptResponse;
import com.finance.financeservice.dto.keys.EncryptRequest;
import com.finance.financeservice.dto.keys.EncryptResponse;
import com.finance.financeservice.dto.sepay.SepayOAuth2TokenResponse;
import com.finance.financeservice.dto.sepay.oauth2.SepayConnectionStatus;
import com.finance.financeservice.mysql.entity.SepayOAuth2Token;
import com.finance.financeservice.mysql.repository.SepayOAuth2TokenRepository;
import com.finance.financeservice.service.client.KeyManagementServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages SePay OAuth2 tokens: exchange authorization code, refresh tokens,
 * encrypt/decrypt token storage, and provide valid access tokens for API calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SepayOAuth2TokenService {

    private final SepayOAuth2Properties oauth2Properties;
    private final SepayOAuth2TokenRepository tokenRepository;
    private final KeyManagementServiceClient keyManagementServiceClient;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Build the authorization URL for a user to initiate SePay OAuth2 connection.
     */
    public String buildAuthorizeUrl(String userId) {
        String state = userId + ":" + UUID.randomUUID();
        return oauth2Properties.getAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + oauth2Properties.getClientId()
                + "&redirect_uri=" + oauth2Properties.getRedirectUri()
                + "&scope=" + oauth2Properties.getScopes().replace(" ", "%20")
                + "&state=" + state;
    }

    /**
     * Exchange authorization code for access and refresh tokens.
     * Called after user authorizes on SePay and is redirected back.
     */
    @Transactional
    public SepayConnectionStatus exchangeCodeForTokens(String code, String userId) {
        log.info("Exchanging authorization code for tokens, userId: {}", userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("redirect_uri", oauth2Properties.getRedirectUri());
        params.add("client_id", oauth2Properties.getClientId());
        params.add("client_secret", oauth2Properties.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<SepayOAuth2TokenResponse> response = restTemplate.exchange(
                    oauth2Properties.getTokenUrl(), HttpMethod.POST, request, SepayOAuth2TokenResponse.class);

            SepayOAuth2TokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || ObjectUtils.isEmpty(tokenResponse.getAccessToken())) {
                log.error("Empty token response from SePay for userId: {}", userId);
                return SepayConnectionStatus.builder()
                        .oauth2Enabled(true)
                        .connected(false)
                        .build();
            }

            // Encrypt and store tokens
            saveTokens(userId, tokenResponse);

            log.info("OAuth2 tokens obtained and stored for userId: {}", userId);
            return SepayConnectionStatus.builder()
                    .oauth2Enabled(true)
                    .connected(true)
                    .scopes(tokenResponse.getScope())
                    .connectedAt(LocalDateTime.now())
                    .tokenValid(true)
                    .build();

        } catch (Exception e) {
            log.error("Error exchanging code for tokens, userId: {}", userId, e);
            return SepayConnectionStatus.builder()
                    .oauth2Enabled(true)
                    .connected(false)
                    .build();
        }
    }

    /**
     * Get a valid decrypted access token for a user.
     * Automatically refreshes if expired.
     */
    public String getValidAccessToken(String userId) {
        Optional<SepayOAuth2Token> tokenOpt = tokenRepository.findByUserId(userId);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isConnected()) {
            log.warn("No OAuth2 token for userId: {}", userId);
            return null;
        }

        SepayOAuth2Token token = tokenOpt.get();

        // Check if access token is expired (with 5 min buffer)
        if (token.getAccessTokenExpiresAt() != null
                && token.getAccessTokenExpiresAt().minusMinutes(5).isBefore(LocalDateTime.now())) {
            log.info("Access token expired for userId: {}, refreshing...", userId);
            return refreshAndGetToken(userId, token);
        }

        // Decrypt and return
        return decryptToken(userId, token.getAccessToken());
    }

    /**
     * Refresh the access token using the refresh token.
     */
    private String refreshAndGetToken(String userId, SepayOAuth2Token token) {
        String decryptedRefreshToken = decryptToken(userId, token.getRefreshToken());
        if (decryptedRefreshToken == null) {
            log.error("Cannot decrypt refresh token for userId: {}", userId);
            return null;
        }

        // Check if refresh token is expired
        if (token.getRefreshTokenExpiresAt() != null
                && token.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired for userId: {}. User needs to re-authorize.", userId);
            token.setConnected(false);
            tokenRepository.save(token);
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", decryptedRefreshToken);
        params.add("client_id", oauth2Properties.getClientId());
        params.add("client_secret", oauth2Properties.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<SepayOAuth2TokenResponse> response = restTemplate.exchange(
                    oauth2Properties.getTokenUrl(), HttpMethod.POST, request, SepayOAuth2TokenResponse.class);

            SepayOAuth2TokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || ObjectUtils.isEmpty(tokenResponse.getAccessToken())) {
                log.error("Empty refresh token response for userId: {}", userId);
                return null;
            }

            // Save new tokens (refresh token also rotates)
            saveTokens(userId, tokenResponse);

            return tokenResponse.getAccessToken();

        } catch (Exception e) {
            log.error("Error refreshing token for userId: {}", userId, e);
            // Mark as disconnected if refresh fails
            token.setConnected(false);
            tokenRepository.save(token);
            return null;
        }
    }

    /**
     * Save encrypted tokens to database.
     */
    private void saveTokens(String userId, SepayOAuth2TokenResponse tokenResponse) {
        SepayOAuth2Token token = tokenRepository.findByUserId(userId)
                .orElse(new SepayOAuth2Token());

        token.setUserId(userId);
        token.setAccessToken(encryptToken(userId, tokenResponse.getAccessToken()));
        token.setRefreshToken(encryptToken(userId, tokenResponse.getRefreshToken()));
        token.setScopes(tokenResponse.getScope());
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn()));
        // Refresh token expires in approximately 1 month
        token.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(30));
        token.setConnected(true);

        tokenRepository.save(token);
    }

    /**
     * Disconnect a user's SePay OAuth2 connection.
     */
    @Transactional
    public void disconnect(String userId) {
        tokenRepository.findByUserId(userId).ifPresent(token -> {
            token.setConnected(false);
            token.setAccessToken(null);
            token.setRefreshToken(null);
            tokenRepository.save(token);
        });
        log.info("Disconnected SePay OAuth2 for userId: {}", userId);
    }

    /**
     * Get the connection status for a user.
     */
    public SepayConnectionStatus getConnectionStatus(String userId) {
        if (!oauth2Properties.isEnabled()) {
            return SepayConnectionStatus.builder()
                    .oauth2Enabled(false)
                    .connected(false)
                    .build();
        }

        Optional<SepayOAuth2Token> tokenOpt = tokenRepository.findByUserId(userId);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isConnected()) {
            return SepayConnectionStatus.builder()
                    .oauth2Enabled(true)
                    .connected(false)
                    .authorizeUrl(buildAuthorizeUrl(userId))
                    .build();
        }

        SepayOAuth2Token token = tokenOpt.get();
        boolean tokenValid = token.getAccessTokenExpiresAt() != null
                && token.getAccessTokenExpiresAt().isAfter(LocalDateTime.now());

        // Even if access token is expired, we can still refresh if refresh token is valid
        boolean canRefresh = token.getRefreshTokenExpiresAt() != null
                && token.getRefreshTokenExpiresAt().isAfter(LocalDateTime.now());

        return SepayConnectionStatus.builder()
                .oauth2Enabled(true)
                .connected(true)
                .scopes(token.getScopes())
                .connectedAt(token.getCreatedAt())
                .tokenValid(tokenValid || canRefresh)
                .build();
    }

    /**
     * Get all connected user IDs (for scheduled sync if needed).
     */
    public List<String> getConnectedUserIds() {
        return tokenRepository.findByConnectedTrue().stream()
                .map(SepayOAuth2Token::getUserId)
                .toList();
    }

    /**
     * Check if OAuth2 mode is enabled.
     */
    public boolean isOAuth2Enabled() {
        return oauth2Properties.isEnabled();
    }

    private String encryptToken(String userId, String plainToken) {
        if (ObjectUtils.isEmpty(plainToken)) return null;
        try {
            EncryptRequest request = EncryptRequest.builder()
                    .userId(userId)
                    .data(plainToken)
                    .build();
            EncryptResponse response = keyManagementServiceClient.encrypt(request);
            if (response != null && response.isSuccess()) {
                return response.getEncryptedData();
            }
        } catch (Exception e) {
            log.error("Error encrypting OAuth2 token for userId: {}", userId, e);
        }
        return null;
    }

    private String decryptToken(String userId, String encryptedToken) {
        if (ObjectUtils.isEmpty(encryptedToken)) return null;
        try {
            DecryptRequest request = DecryptRequest.builder()
                    .userId(userId)
                    .encryptedData(encryptedToken)
                    .build();
            DecryptResponse response = keyManagementServiceClient.decrypt(request);
            if (response != null && response.isSuccess()) {
                return response.getDecryptedData();
            }
        } catch (Exception e) {
            log.error("Error decrypting OAuth2 token for userId: {}", userId, e);
        }
        return null;
    }
}
