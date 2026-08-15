package com.banksphere.customer.otp.dto;

import com.banksphere.customer.otp.OtpPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Called server-to-server by account-service (or a future
 * step-up-protected service), forwarding the acting customer's own
 * bearer token — the same "downstream service independently re-verifies
 * the caller's real token" pattern this codebase has used since Phase 1
 * (see ADR-001/ADR-007), applied here for a same-principal-type,
 * cross-service confirmation rather than a cross-principal-type one.
 */
public record StepUpConfirmRequest(
        @NotNull(message = "challengeId is required")
        java.util.UUID challengeId,

        @NotNull(message = "purpose is required")
        OtpPurpose purpose,

        @Valid
        @NotNull(message = "transferContext is required")
        TransferStepUpContext transferContext
) {
}
