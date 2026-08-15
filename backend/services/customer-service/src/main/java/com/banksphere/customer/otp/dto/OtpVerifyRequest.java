package com.banksphere.customer.otp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record OtpVerifyRequest(
        @NotNull(message = "challengeId is required")
        UUID challengeId,

        @NotBlank(message = "otp is required")
        @Pattern(regexp = "^\\d{4,8}$", message = "otp must be 4-8 digits")
        String otp
) {
    /** Never let an accidental log line print the OTP value. */
    @Override
    public String toString() {
        return "OtpVerifyRequest[challengeId=%s, otp=REDACTED]".formatted(challengeId);
    }
}
