package com.finance.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * GroupAccount entity - stores only reference to Account.
 * Actual account data (label, bankBrandName, accountNumber, accumulated, etc.)
 * is fetched real-time from FinanceService via Feign client.
 */
@Entity
@Table(name = "group_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_account", columnNames = {"group_id", "account_id"})
})
@Getter
@Setter
public class GroupAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "bank_account_id", length = 200)
    private String bankAccountId; // Kept for transaction queries

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;
}





