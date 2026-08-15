package com.banksphere.account.exception;

/**
 * Thrown when a {@code stepUpChallengeId} was presented but
 * customer-service's confirm call rejected it — wrong customer, context
 * mismatch (tampering: amount/recipient/source changed after the OTP was
 * verified), not yet verified, already used (replay), or expired. The
 * specific reason from customer-service's own response is preserved in
 * the message (customer-service's own error messages are already
 * generic/safe — see its GlobalExceptionHandler) but the HTTP status here
 * is always {@code 403}: whatever the precise reason, the caller is not
 * authorized to execute this specific operation right now.
 */
public class StepUpVerificationFailedException extends RuntimeException {

    public StepUpVerificationFailedException(String message) {
        super(message);
    }
}
