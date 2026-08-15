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
 * {@code customerId} is nullable and deliberately never used for an
 * access decision on its own before the challenge is resolved to a real
 * customer — a challenge for an unregistered {@code identifier} is a real
 * row with {@code customerId = null}, so the request/response shape is
 * identical to a challenge for a registered one (see
 * OtpServiceImpl#requestOtp and ADR-009's account-enumeration section).
 *
 * <p>{@code contextHash} is null for {@code LOGIN} (nothing to bind to)
 * and a SHA-256 hex digest of the canonical operation parameters for a
 * {@code STEP_UP_*} purpose — see {@link OtpContextHasher}. This is the
 * mechanism that makes "request OTP for ₹1,000, then submit ₹100,000"
 * fail: the confirm call recomputes the hash from the operation actually
 * being executed and compares it against this stored value.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallenge {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private OtpPurpose purpose;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "context_hash", length = 64)
    private String contextHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OtpChallengeStatus status = OtpChallengeStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requested_ip", length = 64)
    private String requestedIp;

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

    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }
}
