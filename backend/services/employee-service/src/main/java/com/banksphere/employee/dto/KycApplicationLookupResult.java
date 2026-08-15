package com.banksphere.employee.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Deserialization target for kyc-service's {@code KycApplicationDetailResponse}. See ADR-008. */
public record KycApplicationLookupResult(
        UUID id,
        String status,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewReason,
        List<String> missingDocumentTypes,
        List<KycDocumentLookupResult> documents
) {
}
