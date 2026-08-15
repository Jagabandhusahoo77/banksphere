package com.banksphere.customer.exception;

/**
 * Thrown when the operation actually being executed (recomputed
 * context hash) does not match what the customer verified an OTP for —
 * the "request OTP for ₹1,000, then submit ₹100,000" tampering case.
 * Mapped to {@code 403}: the caller's identity is valid, but they are not
 * authorized for *this* operation, only the one they actually proved
 * intent for. See ADR-009's operation-binding section.
 */
public class StepUpContextMismatchException extends RuntimeException {

    public StepUpContextMismatchException() {
        super("This step-up authorization does not match the requested operation");
    }
}
