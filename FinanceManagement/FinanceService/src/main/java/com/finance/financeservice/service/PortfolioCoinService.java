package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.PortfolioCoin;

import java.util.List;

public interface PortfolioCoinService {
    List<PortfolioCoin> findByUserId(String userId);
    PortfolioCoin addCoin(PortfolioCoin coin);
    void removeCoin(String userId, String coinId);
}
