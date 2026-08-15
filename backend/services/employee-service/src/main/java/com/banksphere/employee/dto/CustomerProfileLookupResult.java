package com.banksphere.employee.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization target for customer-service's {@code GET
 * /api/v1/customers/employee-lookup/{id}/profile} response — the fuller
 * Phase 9C profile, distinct from {@link CustomerLookupResult} (the
 * Phase 9B slim cash-deposit-confirmation shape, unchanged). See
 * ADR-008.
 */
public record CustomerProfileLookupResult(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String status,
        Instant createdAt
) {
}
