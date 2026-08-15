package com.banksphere.employee.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code operationReference} (e.g. {@code CD-A1B2C3D4E5}) is THIS
 * service's own reference for the operation — it is NOT
 * transaction-service's {@code TXN-...} reference, which is carried
 * separately in {@code transactionReference} (nullable — best-effort
 * ledger recording, see ADR-001/ADR-007; a null value here means the
 * deposit itself still succeeded, only the ledger write failed).
 */
public record CashDepositResponse(
        String operationReference,
        UUID accountId,
        String accountNumber,
        BigDecimal newBalance,
        String currency,
        String transactionReference,
        String status,
        String performedBy,
        String branchCode
) {
}
