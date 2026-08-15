package com.banksphere.customer.otp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code tokenHash} is a SHA-256 hex digest of the real, opaque,
 * cryptographically random refresh token value — the plaintext value is
 * returned to the browser exactly once (as an HttpOnly cookie, never in a
 * JSON body) and never stored. See ADR-009's refresh-token section for why
 * SHA-256 (not BCrypt) is the right hash here.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RefreshTokenStatus status = RefreshTokenStatus.ACTIVE;

    /** Set when this token is rotated out — points at its successor, for reuse-detection's family-wide revocation. */
    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
