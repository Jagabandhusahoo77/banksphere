package com.banksphere.transaction.service;

import java.util.UUID;

/**
 * transaction-service knows accountId but not which customer owns that
 * account — account-service owns that relationship. Rather than trusting
 * an accountId supplied by the browser, every read of transaction data
 * for an account calls back to account-service (forwarding the caller's
 * own JWT) and asks "does the authenticated caller own this account?"
 * See docs/security/authorization.md for the full reasoning, including
 * why this check is deliberately NOT performed on the internal create
 * path (POST /api/v1/transactions).
 */
public interface AccountOwnershipClient {

    /**
     * @return true only if account-service responds 2xx to
     * {@code GET /api/v1/accounts/{accountId}} using the given token —
     * i.e. the account exists AND is owned by that token's customer.
     * Any other outcome (403, 404, timeout, 5xx) returns false: this
     * fails closed, not open, if account-service can't be reached.
     */
    boolean isOwnedByCaller(UUID accountId, String bearerToken);
}
