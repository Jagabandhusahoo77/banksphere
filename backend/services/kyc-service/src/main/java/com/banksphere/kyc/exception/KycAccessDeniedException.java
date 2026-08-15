package com.banksphere.kyc.exception;

/**
 * Thrown when an authenticated customer's own JWT-derived {@code
 * customerId} does not match the {@code customerId} on the KYC
 * application/document being accessed — a customer must never be able to
 * read or mutate another customer's KYC data by guessing/enumerating a
 * UUID (see CLAUDE.md's "never trust an ID for an access decision" rule).
 * Mapped to {@code 403}, never {@code 404}: the resource genuinely
 * exists, the caller is just not allowed to see it, consistent with the
 * account/beneficiary/transaction-service precedent.
 */
public class KycAccessDeniedException extends RuntimeException {

    public KycAccessDeniedException(String message) {
        super(message);
    }
}
