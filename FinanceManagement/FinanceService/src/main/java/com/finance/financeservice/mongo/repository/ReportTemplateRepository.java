package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.ReportTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportTemplateRepository extends MongoRepository<ReportTemplate, String> {

    List<ReportTemplate> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByIdAndUserId(String id, String userId);
}
