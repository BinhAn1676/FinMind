package com.finance.financeservice.dto.sepay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response from SePay OAuth2 endpoints.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SepayOAuth2ErrorResponse {
    @JsonProperty("error")
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;
}
