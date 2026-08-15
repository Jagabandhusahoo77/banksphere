package com.banksphere.kyc.dto;

import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The customer-facing view of their own KYC application — see KycApplicationDetailResponse for the employee equivalent. */
public record KycApplicationResponse(
        UUID id,
        KycStatus status,
        String panNumber,
        String occupation,
        String annualIncomeRange,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewReason,
        List<DocumentType> missingDocumentTypes,
        List<KycDocumentResponse> documents,
        Instant createdAt,
        Instant updatedAt
) {
}
