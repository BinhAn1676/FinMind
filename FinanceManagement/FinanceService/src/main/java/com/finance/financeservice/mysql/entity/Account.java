package com.finance.financeservice.mysql.entity;

import com.finance.financeservice.constant.AccountStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Account extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String bankAccountId;
    private String accountHolderName;
    private String accountHolderNameHash;
    private String accountNumber;
    private String accountNumberHash;
    private String accumulated;
    private String label;
    private String active;
    private String bankShortName;
    private String bankShortNameHash;
    private String bankFullName;
    private String bankFullNameHash;
    private String bankBin;
    private String bankBinHash;
    private String bankCode;
    private String bankCodeHash;
    private String userId;
}
