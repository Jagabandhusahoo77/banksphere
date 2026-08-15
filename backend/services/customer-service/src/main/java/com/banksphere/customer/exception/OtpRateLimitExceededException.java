package com.banksphere.customer.exception;

/** Mapped to {@code 429 Too Many Requests}. See {@code OtpRateLimiter}'s own javadoc for the demo-only, in-memory scope of this protection. */
public class OtpRateLimitExceededException extends RuntimeException {

    public OtpRateLimitExceededException() {
        super("Too many requests. Please wait before trying again.");
    }
}
