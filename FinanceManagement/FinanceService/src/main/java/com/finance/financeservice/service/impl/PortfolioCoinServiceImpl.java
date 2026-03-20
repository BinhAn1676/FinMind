package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.PortfolioCoin;
import com.finance.financeservice.mongo.repository.PortfolioCoinRepository;
import com.finance.financeservice.service.PortfolioCoinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioCoinServiceImpl implements PortfolioCoinService {

    private final PortfolioCoinRepository portfolioCoinRepository;

    @Override
    public List<PortfolioCoin> findByUserId(String userId) {
        return portfolioCoinRepository.findByUserId(userId);
    }

    @Override
    public PortfolioCoin addCoin(PortfolioCoin coin) {
        return portfolioCoinRepository.findByUserIdAndCoinId(coin.getUserId(), coin.getCoinId())
                .orElseGet(() -> portfolioCoinRepository.save(coin));
    }

    @Override
    public void removeCoin(String userId, String coinId) {
        portfolioCoinRepository.deleteByUserIdAndCoinId(userId, coinId);
    }
}
