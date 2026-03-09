package com.finance.fileservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkLiveUrlRequest {
    @NotEmpty
    private List<String> ids;
    private Integer expiry;
}


