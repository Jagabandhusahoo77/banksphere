package com.banksphere.employee.dto;

import com.banksphere.employee.entity.CashDepositOperation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code customerName} is resolved live from customer-service at read
 * time (never stored in {@link CashDepositOperation} — see that entity's
 * javadoc and ADR-007, Decision 9) — {@code null} if that lookup failed,
 * so a display hiccup never hides a real operation record.
 */
public record CashDepositHistoryEntry(
        String operationReference,
        String customerName,
        String accountNumber,
        BigDecimal amount,
        String currency,
        String status,
        String transactionReference,
        Instant createdAt
) {
    public static CashDepositHistoryEntry from(CashDepositOperation operation, String customerName) {
        return new CashDepositHistoryEntry(
                operation.getOperationReference(), customerName, operation.getAccountNumber(),
                operation.getAmount(), operation.getCurrency(), operation.getStatus(),
                operation.getTransactionReference(), operation.getCreatedAt());
    }
}
