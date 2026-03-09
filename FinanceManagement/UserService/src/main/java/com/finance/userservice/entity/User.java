package com.finance.userservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String phone;
    private LocalDate birthDay;
    private String address;
    private String fullName;
    private String bankToken;
    private String bankTokenHash;
    private String phoneHash;
    private String emailHash;
    private String addressHash;
    private String fullNameHash;
    // Profile fields
    private String avatar; // File key for avatar
    private String bio;
    private String dateOfBirth; // String format for frontend compatibility
}