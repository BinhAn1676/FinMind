package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.GroupPlanning;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GroupPlanningRepository extends MongoRepository<GroupPlanning, String> {
    List<GroupPlanning> findByGroupId(Long groupId);
    
    List<GroupPlanning> findByGroupIdAndCategory(Long groupId, String category);
    
    List<GroupPlanning> findByGroupIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long groupId, LocalDate date1, LocalDate date2);
    
    List<GroupPlanning> findByGroupIdAndCategoryAndPlanType(
            Long groupId, String category, GroupPlanning.PlanType planType);
    
    void deleteByGroupId(Long groupId);
}


