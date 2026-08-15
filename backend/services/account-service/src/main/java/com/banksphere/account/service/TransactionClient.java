package com.banksphere.account.service;

import com.banksphere.account.dto.TransactionRecordRequest;
import com.banksphere.account.dto.TransactionRecordResult;

import java.util.Optional;

public interface TransactionClient {

    /**
     * Records a completed deposit/withdrawal as a transaction ledger entry
     * in transaction-service. Implementations should not throw on failure;
     * a failed recording is logged so it does not roll back the balance
     * change that already succeeded in this service's own transaction.
     * Returns the real {@code transactionReference} on success, or {@code
     * Optional.empty()} if recording failed — callers that don't need it
     * (deposit/withdraw/transfer, all unchanged from before Phase 9B)
     * simply ignore the return value; the new employee-deposit path uses
     * it to report a real reference rather than fabricating one (see
     * ADR-007). This is purely an additive signature change — the
     * best-effort, never-rethrows contract is unchanged.
     *
     * @param bearerToken the raw JWT (no "Bearer " prefix) from the
     *                    original request — the caller's own customer
     *                    token for a customer-initiated deposit/withdraw/
     *                    transfer, or the acting employee's own token for
     *                    an employee-initiated cash deposit (Phase 9B) —
     *                    forwarded so transaction-service's own auth
     *                    requirement is satisfied — see
     *                    docs/security/authorization.md's section on
     *                    transaction-service.
     */
    Optional<TransactionRecordResult> recordTransaction(TransactionRecordRequest request, String bearerToken);
}
