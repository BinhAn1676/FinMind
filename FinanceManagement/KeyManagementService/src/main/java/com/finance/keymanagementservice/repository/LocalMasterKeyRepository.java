package com.finance.keymanagementservice.repository;

import com.finance.keymanagementservice.entity.LocalMasterKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocalMasterKeyRepository extends JpaRepository<LocalMasterKey, Long> {
    
    /**
     * Find active local master key by key name
     */
    @Query("SELECT lmk FROM LocalMasterKey lmk WHERE lmk.keyName = :keyName AND lmk.isActive = true")
    Optional<LocalMasterKey> findActiveByKeyName(@Param("keyName") String keyName);
    
    /**
     * Find local master key by key name (including inactive)
     */
    Optional<LocalMasterKey> findByKeyName(String keyName);
    
    /**
     * Check if active local master key exists
     */
    @Query("SELECT COUNT(lmk) > 0 FROM LocalMasterKey lmk WHERE lmk.keyName = :keyName AND lmk.isActive = true")
    boolean existsActiveByKeyName(@Param("keyName") String keyName);
    
    /**
     * Deactivate all local master keys
     */
    @Query("UPDATE LocalMasterKey lmk SET lmk.isActive = false")
    void deactivateAll();
}

