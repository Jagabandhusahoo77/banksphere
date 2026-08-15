package com.banksphere.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code transferId} is an account-service-local correlation id, minted
 * for this response only — it is NOT a transaction-service ledger
 * reference. A transfer produces two independent ledger rows in
 * transaction-service (one on the source account, one on the destination
 * account), each with its own separately generated {@code TXN-...}
 * reference; transaction-service owns that reference format and this
 * service does not (and must not) invent a competing one. The two legs
 * are not currently linked by a shared id in the ledger itself — see
 * ADR-004 for why that's an accepted limitation in this phase, not an
 * oversight.
 * <p>
 * {@code destinationAccountId} is deliberately absent (Phase 8B, ADR-005)
 * — the caller identified the recipient by account number + IFSC and
 * never needs their internal id back, even after a successful transfer.
 * {@code sourceAccountId} stays a UUID: it's the caller's own account,
 * which they already know (they selected it from their own account list).
 * <p>
 * {@code status} is always {@code "COMPLETED"} in this phase: the balance
 * mutation is synchronous and any failure throws before this response is
 * ever constructed, so there is no PENDING/FAILED state to represent yet.
 */
public record TransferResponse(
        UUID transferId,
        UUID sourceAccountId,
        String destinationAccountNumber,
        String destinationIfsc,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {
}
