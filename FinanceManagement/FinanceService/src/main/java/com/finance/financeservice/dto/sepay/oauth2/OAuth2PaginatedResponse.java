package com.finance.financeservice.dto.sepay.oauth2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response from SePay OAuth2 API endpoints.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuth2PaginatedResponse<T> {
    private String status;
    private List<T> data;
    private Meta meta;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meta {
        private Pagination pagination;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Pagination {
        private int total;
        private int perPage;
        private int currentPage;
        private int lastPage;
    }
}
