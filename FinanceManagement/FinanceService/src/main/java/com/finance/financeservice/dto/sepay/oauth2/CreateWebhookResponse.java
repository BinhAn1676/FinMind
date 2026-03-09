package com.finance.financeservice.dto.sepay.oauth2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from creating a webhook via SePay OAuth2 API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateWebhookResponse {
    private String message;
    private Integer id;
}
