package com.finance.financeservice.mysql.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "external_accounts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExternalAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String type; // cash, realestate, crypto

    @Column(nullable = false)
    private String accumulated;

    @Column(columnDefinition = "TEXT")
    private String description;
}

