package com.banksphere.transaction.service;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {

    /**
     * A valid JWT is required to call this (enforced by SecurityConfig)
     * but account ownership is deliberately NOT re-verified here — see
     * AccountOwnershipClient's javadoc and docs/security/authorization.md
     * for why.
     */
    TransactionResponse createTransaction(TransactionCreateRequest request);

    /** Throws TransactionAccessDeniedException if the transaction's account isn't owned by the caller (verified via account-service). */
    TransactionResponse getTransaction(UUID id, String bearerToken);

    /** Throws TransactionAccessDeniedException if the account isn't owned by the caller (verified via account-service). */
    PageResponse<TransactionResponse> getTransactionsByAccount(UUID accountId, Pageable pageable, String bearerToken);

    /**
     * Phase 9C — the Customer 360 aggregation's transactions section.
     * Deliberately does NOT call {@code AccountOwnershipClient} — an
     * employee token was never issued for a specific customer, so
     * "does the caller own this account" is the wrong question here; the
     * {@code @PreAuthorize("hasAuthority('TRANSACTION_VIEW')")} check at
     * the controller layer is the authorization boundary instead. See
     * ADR-008.
     */
    PageResponse<TransactionResponse> getTransactionsByAccountForEmployee(UUID accountId, Pageable pageable);
}
