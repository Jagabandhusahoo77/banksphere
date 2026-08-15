package com.banksphere.account.dto;

import java.util.UUID;

/**
 * A partial view of transaction-service's real {@code TransactionResponse}
 * — this service only needs the {@code transactionReference} (the real
 * {@code TXN-...} string) to hand back to a caller that wants to display
 * it (see the new employee-deposit endpoint). Jackson ignores the
 * response fields this record doesn't declare, so no shared DTO module is
 * needed for this partial mapping — consistent with every other
 * cross-service DTO in this codebase being its own, independently
 * maintained copy.
 */
public record TransactionRecordResult(UUID id, String transactionReference) {
}
