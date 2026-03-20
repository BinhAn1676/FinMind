package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.BillReminder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BillReminderRepository extends MongoRepository<BillReminder, String> {
    List<BillReminder> findByUserIdOrderByCreatedAtDesc(String userId);
    List<BillReminder> findByIsActiveTrue();
}
