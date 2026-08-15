package com.banksphere.customer.otp;

import java.util.UUID;

/** Returned by {@code OtpService.verifyOtp} on success — the resolved, real customer the challenge belonged to. */
public record OtpVerificationResult(UUID customerId, OtpPurpose purpose) {
}
