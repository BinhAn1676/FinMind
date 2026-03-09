package com.finance.keymanagementservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "local_master_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LocalMasterKey extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "key_name", nullable = false, unique = true)
    private String keyName;
    
    @Column(name = "encrypted_master_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedMasterKey;
    
    @Column(name = "key_version", nullable = false)
    private String keyVersion;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}

