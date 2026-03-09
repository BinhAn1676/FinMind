package com.finance.fileservice.repository;

import com.finance.fileservice.entity.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends MongoRepository<FileEntity, String> {
    
    Optional<FileEntity> findByUserIdAndPurpose(String userId, String purpose);
    
    List<FileEntity> findByUserId(String userId);
    
    List<FileEntity> findByPurpose(String purpose);
    
    @Query("{'userId': ?0, 'purpose': ?1}")
    Optional<FileEntity> findFileByUserIdAndPurpose(String userId, String purpose);
    
    @Query("{'userId': ?0}")
    Page<FileEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    @Query("{'userId': ?0, 'purpose': ?1}")
    Page<FileEntity> findByUserIdAndPurposeOrderByCreatedAtDesc(String userId, String purpose, Pageable pageable);
}
