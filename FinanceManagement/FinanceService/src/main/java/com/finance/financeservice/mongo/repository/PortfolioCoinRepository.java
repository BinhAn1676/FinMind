package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.PortfolioCoin;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioCoinRepository extends MongoRepository<PortfolioCoin, String> {
    List<PortfolioCoin> findByUserId(String userId);
    Optional<PortfolioCoin> findByUserIdAndCoinId(String userId, String coinId);
    void deleteByUserIdAndCoinId(String userId, String coinId);
    boolean existsByUserIdAndCoinId(String userId, String coinId);
}
