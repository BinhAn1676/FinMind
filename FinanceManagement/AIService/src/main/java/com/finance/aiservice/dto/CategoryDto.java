package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Category DTO for AI Service.
 *
 * Represents a spending/income category.
 */
public record CategoryDto(
    @JsonProperty("id")
    String id,

    @JsonProperty("userId")
    String userId,

    @JsonProperty("name")
    String name,

    @JsonProperty("isDefault")
    Boolean isDefault
) {}
