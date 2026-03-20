package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.GroupExpense;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupExpenseRepository extends MongoRepository<GroupExpense, String> {
    List<GroupExpense> findByGroupIdOrderByCreatedAtDesc(Long groupId);
    void deleteByGroupId(Long groupId);
}
