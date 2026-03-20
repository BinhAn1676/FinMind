package com.finance.financeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public boolean isLast() {
        return page >= totalPages - 1;
    }

    public boolean isFirst() {
        return page == 0;
    }
}
