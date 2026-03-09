package com.finance.financeservice.dto.sepay.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bank info nested inside OAuth2 bank account response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2BankInfo {
    @JsonProperty("short_name")
    private String shortName;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("code")
    private String code;

    @JsonProperty("bin")
    private String bin;

    @JsonProperty("icon_url")
    private String iconUrl;

    @JsonProperty("logo_url")
    private String logoUrl;
}
