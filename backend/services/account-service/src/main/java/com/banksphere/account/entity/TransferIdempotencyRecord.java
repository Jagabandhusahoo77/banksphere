package com.banksphere.account.entity;

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
 * The exactly-once guarantee for {@code POST /api/v1/accounts/transfer}
 * — see ADR-009's idempotency section. {@code responseSnapshot} is the
 * serialized {@code TransferResponse} JSON from the first successful
 * execution, replayed verbatim on a retry rather than re-executing the
 * transfer — a double-click, a network retry, or a browser refresh that
 * resubmits the same {@code idempotencyKey} must see the SAME result, not
 * a second money movement.
 */
@Entity
@Table(name = "transfer_idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferIdempotencyRecord {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransferIdempotencyStatus status = TransferIdempotencyStatus.IN_PROGRESS;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
