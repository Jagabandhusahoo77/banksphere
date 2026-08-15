package com.banksphere.customer.otp.dto;

import java.util.UUID;

/**
 * {@code message} is always the same generic string regardless of whether
 * {@code identifier} matched a real customer — see ADR-009. {@code
 * challengeId} is always a freshly generated, real UUID too, for the same
 * reason: its presence/shape must not itself be a signal.
 */
public record OtpRequestResponse(String message, UUID challengeId) {

    private static final String GENERIC_MESSAGE = "If the account is eligible, an OTP has been sent.";

    public static OtpRequestResponse of(UUID challengeId) {
        return new OtpRequestResponse(GENERIC_MESSAGE, challengeId);
    }
}
