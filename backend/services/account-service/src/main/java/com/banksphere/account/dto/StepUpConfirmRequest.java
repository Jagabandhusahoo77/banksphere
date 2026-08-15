package com.banksphere.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The request body sent to customer-service's {@code POST
 * /api/v1/auth/step-up/confirm} — an independently-maintained copy of
 * that service's own {@code StepUpConfirmRequest}/{@code
 * TransferStepUpContext} shape (no shared module — see backend/README.md),
 * not a shared DTO. Field names/order must match exactly, since
 * customer-service recomputes a hash over these same fields and compares
 * it against what the customer originally verified an OTP for.
 */
public record StepUpConfirmRequest(
        UUID challengeId,
        String purpose,
        TransferContext transferContext
) {
    public record TransferContext(
            UUID sourceAccountId,
            String destinationAccountNumber,
            String destinationIfsc,
            BigDecimal amount,
            String currency
    ) {
    }
}
