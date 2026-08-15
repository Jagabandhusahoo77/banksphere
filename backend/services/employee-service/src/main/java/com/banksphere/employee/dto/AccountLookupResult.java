package com.banksphere.employee.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deserialization target for account-service's {@code
 * GET /api/v1/accounts/employee-lookup[...]} responses — a partial view
 * of account-service's own {@code AccountResponse}; Jackson ignores
 * fields this record doesn't declare. Not shared code (no shared module
 * — see backend/README.md's own note on this), just an independently
 * maintained copy, same as every other cross-service DTO in this codebase.
 */
public record AccountLookupResult(
        UUID id,
        UUID customerId,
        String accountNumber,
        String ifsc,
        String accountType,
        BigDecimal balance,
        String currency,
        String status
) {
}
