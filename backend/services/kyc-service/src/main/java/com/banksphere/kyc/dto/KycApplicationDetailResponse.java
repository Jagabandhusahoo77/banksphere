package com.banksphere.kyc.dto;

import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The employee-facing review-screen view — includes fields (customerId, currentReviewerId, review history) a customer never sees. */
public record KycApplicationDetailResponse(
        UUID id,
        UUID customerId,
        KycStatus status,
        String panNumber,
        String occupation,
        String annualIncomeRange,
        UUID currentReviewerId,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String reviewReason,
        List<DocumentType> missingDocumentTypes,
        List<KycDocumentResponse> documents,
        List<KycStatusHistoryResponse> statusHistory,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
