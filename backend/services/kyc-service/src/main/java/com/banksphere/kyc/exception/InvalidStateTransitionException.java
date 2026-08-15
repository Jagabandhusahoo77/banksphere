package com.banksphere.kyc.exception;

import com.banksphere.kyc.entity.KycStatus;

/**
 * Thrown by {@code KycStateMachine.requireValidTransition} for any
 * transition outside the locked-in table — mapped to {@code 422} by
 * {@code GlobalExceptionHandler} (a well-formed request whose business
 * operation cannot proceed given the application's current state).
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(KycStatus from, KycStatus to) {
        super("Cannot transition KYC application from " + from + " to " + to);
    }
}
