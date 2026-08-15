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
 * {@code storageReference} is an opaque key into {@code DocumentStorage}
 * (see the {@code storage} package) — never a public URL, never exposed
 * to the customer, and only ever used server-side to stream content back
 * through the employee document-content endpoint. See ADR-008.
 */
@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocument {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "kyc_application_id", nullable = false)
    private UUID kycApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus documentStatus = DocumentStatus.PENDING;

    @Column(name = "storage_reference", nullable = false, length = 255)
    private String storageReference;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** The employee who verified OR rejected this document — null while PENDING. */
    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = Instant.now();
        }
        if (this.documentStatus == null) {
            this.documentStatus = DocumentStatus.PENDING;
        }
    }
}
