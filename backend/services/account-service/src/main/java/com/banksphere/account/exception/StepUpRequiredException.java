package com.banksphere.account.exception;

/**
 * Thrown when {@code StepUpPolicy} requires step-up authentication for
 * this transfer (amount at or above the configured threshold) but the
 * request carries no {@code stepUpChallengeId} at all. Mapped to {@code
 * 403} — the caller's identity is valid, but this specific operation
 * additionally requires a factor they have not yet presented. See
 * ADR-009.
 */
public class StepUpRequiredException extends RuntimeException {

    public StepUpRequiredException() {
        super("Step-up authentication is required for this operation");
    }
}
