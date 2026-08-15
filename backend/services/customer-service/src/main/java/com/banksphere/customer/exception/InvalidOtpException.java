package com.banksphere.customer.exception;

/**
 * Thrown for every OTP-verification failure — wrong code, expired
 * challenge, too many attempts, or an already-consumed/executed
 * challenge. Deliberately one exception type with one generic message,
 * mirroring {@link InvalidCredentialsException}'s reasoning exactly: the
 * response must never distinguish "wrong code" from "expired" from
 * "already used," since each of those distinctions is a small information
 * leak an attacker could use to refine a guess. See ADR-009.
 */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException() {
        super("Invalid or expired OTP");
    }
}
