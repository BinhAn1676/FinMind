package com.finance.keymanagementservice.repository;

import com.finance.keymanagementservice.entity.AesKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AesKeyRepository extends JpaRepository<AesKey, Long> {
    
    /**
     * Find active AES key by user ID
     */
    @Query("SELECT ak FROM AesKey ak WHERE ak.userId = :userId AND ak.isActive = true")
    Optional<AesKey> findActiveByUserId(@Param("userId") String userId);
    
    /**
     * Find AES key by user ID (including inactive)
     */
    Optional<AesKey> findByUserId(String userId);
    
    /**
     * Check if user has an active AES key
     */
    @Query("SELECT COUNT(ak) > 0 FROM AesKey ak WHERE ak.userId = :userId AND ak.isActive = true")
    boolean existsActiveByUserId(@Param("userId") String userId);
    
    /**
     * Deactivate all keys for a user
     */
    @Query("UPDATE AesKey ak SET ak.isActive = false WHERE ak.userId = :userId")
    void deactivateAllByUserId(@Param("userId") String userId);
    
}

