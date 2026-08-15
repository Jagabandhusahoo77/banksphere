package com.banksphere.kyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code customerId} references customer-service's own primary key by
 * value only — no database-level foreign key, since that's a different
 * database (see docs/database/README.md's "no cross-database foreign
 * keys" section, unchanged principle, now applied to a fifth database).
 * Likewise {@code currentReviewerId}/{@code reviewedBy} reference
 * employee-service's `employees.id` by value only.
 *
 * <p>{@code @Version} makes lost-update protection automatic: two
 * employees loading the same application, each in their own {@code
 * @Transactional} service method, racing to record a decision — whichever
 * commits first wins, and Hibernate throws {@code
 * ObjectOptimisticLockingFailureException} for the second, exactly the
 * same mechanism (and the same proof-by-real-Postgres-integration-test
 * approach) already established for {@code Account.version} since Phase
 * 7A. See ADR-008 and {@code integration/KycApplicationReviewConcurrencyIT}.
 */
@Entity
@Table(name = "kyc_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycApplication {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private KycStatus status = KycStatus.DRAFT;

    /** Illustrative demo fields only — see DocumentType's own javadoc and ADR-008; not a claim of regulatory completeness. */
    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "annual_income_range", length = 30)
    private String annualIncomeRange;

    /** Set by {@code start-review} — informational ("who's actively looking at this"), not a pessimistic lock. See ADR-008. */
    @Column(name = "current_reviewer_id")
    private UUID currentReviewerId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** The employee who made the FINAL decision (approve/reject) — null until one of those happens. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /** Customer-visible: a rejection reason, or an additional-information request message. Never internal-only text. */
    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = KycStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
