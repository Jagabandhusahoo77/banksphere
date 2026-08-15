package com.banksphere.employee.dto;

import java.util.UUID;

/** Deserialization target for kyc-service's {@code KycDocumentResponse}, nested within {@link KycApplicationLookupResult}. */
public record KycDocumentLookupResult(
        UUID id,
        String documentType,
        String documentStatus,
        String originalFileName,
        String rejectionReason
) {
}
