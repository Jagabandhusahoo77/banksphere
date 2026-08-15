package com.banksphere.customer.exception;

/** Missing, malformed, expired, or revoked refresh token. Mapped to {@code 401} — this is purely an authentication failure. */
public class RefreshTokenInvalidException extends RuntimeException {

    public RefreshTokenInvalidException() {
        super("Invalid or expired refresh token");
    }
}
