package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.ReportSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportScheduleRepository extends MongoRepository<ReportSchedule, String> {

    List<ReportSchedule> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ReportSchedule> findByActiveTrue();

    void deleteByIdAndUserId(String id, String userId);
}
