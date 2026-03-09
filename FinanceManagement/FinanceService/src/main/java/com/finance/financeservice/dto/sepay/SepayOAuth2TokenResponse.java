package com.finance.financeservice.dto.sepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from SePay OAuth2 token endpoint.
 * POST https://my.sepay.vn/oauth/token
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SepayOAuth2TokenResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("scope")
    private String scope;
}
