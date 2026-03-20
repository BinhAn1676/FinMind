package com.finance.financeservice.service;

import com.finance.financeservice.dto.PageResponse;
import com.finance.financeservice.mongo.document.InvestmentLot;

import java.util.List;

public interface InvestmentLotService {
    List<InvestmentLot> findByUserId(String userId);
    PageResponse<InvestmentLot> findByUserIdFiltered(String userId, String symbol, String dateFrom, String dateTo, int page, int size);
    InvestmentLot create(InvestmentLot lot);
    InvestmentLot update(String id, InvestmentLot lot);
    void delete(String id);
    void deleteByUserIdAndSymbol(String userId, String symbol);
}
