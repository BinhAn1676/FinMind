package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.InvestmentLot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestmentLotRepository extends MongoRepository<InvestmentLot, String> {
    List<InvestmentLot> findByUserIdOrderByBuyDateDesc(String userId);
    List<InvestmentLot> findByUserIdAndSymbol(String userId, String symbol);
}
