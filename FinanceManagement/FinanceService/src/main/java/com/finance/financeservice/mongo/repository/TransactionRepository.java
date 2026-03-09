package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    // add custom query methods if needed
}