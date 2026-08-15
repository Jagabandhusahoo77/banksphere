package com.banksphere.kyc.entity;

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
 * A real, queryable status-change trail for the Review screen's "review
 * history" — structured, not the same thing as the free-form structured
 * *log lines* {@code KycAuditLog} writes (that's for a future log
 * shipper/Audit Service; this is for the UI to render directly). {@code
 * changedByEmployeeId} is null for a customer-initiated transition
 * (submit, resubmit) — never fabricated.
 */
@Entity
@Table(name = "kyc_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycStatusHistory {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "kyc_application_id", nullable = false)
    private UUID kycApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private KycStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 40)
    private KycStatus toStatus;

    /** Null for a customer-initiated transition (submit/resubmit). */
    @Column(name = "changed_by_employee_id")
    private UUID changedByEmployeeId;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }
}
