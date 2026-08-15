package com.banksphere.customer.exception;

/**
 * Thrown when a step-up challenge exists and belongs to the caller and
 * matches the operation, but is in the wrong lifecycle state to confirm —
 * not yet {@code VERIFIED} (OTP never entered), already {@code EXECUTED}
 * (replay — see ADR-009's idempotency/replay section), or expired.
 * Mapped to {@code 409}: a real conflict with the challenge's current
 * state, not a validation error and not an authorization failure.
 */
public class StepUpNotReadyException extends RuntimeException {

    public StepUpNotReadyException(String message) {
        super(message);
    }
}
