package com.banksphere.employee.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Deserialization target for transaction-service's {@code TransactionResponse}, within a {@link RemotePage}. See ADR-008. */
public record TransactionLookupResult(
        UUID id,
        String transactionReference,
        UUID accountId,
        String transactionType,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        Instant createdAt
) {
}
