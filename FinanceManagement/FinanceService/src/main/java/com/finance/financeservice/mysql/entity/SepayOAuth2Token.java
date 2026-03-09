package com.finance.financeservice.mysql.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stores encrypted OAuth2 tokens per user for SePay integration.
 * Access tokens expire in 1 hour, refresh tokens expire in 1 month.
 */
@Entity
@Table(name = "sepay_oauth2_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SepayOAuth2Token extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    /** Encrypted access token */
    @Column(columnDefinition = "TEXT")
    private String accessToken;

    /** Encrypted refresh token */
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    /** Scopes granted by user */
    private String scopes;

    /** When the access token expires */
    private LocalDateTime accessTokenExpiresAt;

    /** When the refresh token expires (approximately 1 month from issue) */
    private LocalDateTime refreshTokenExpiresAt;

    /** Whether the OAuth2 connection is active */
    private boolean connected = false;

    /** SePay user ID extracted from JWT sub claim (optional) */
    private String sepayUserId;
}
