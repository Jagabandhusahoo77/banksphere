package com.banksphere.customer.otp.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code expiresAt} is the window to actually complete the protected operation (e.g. call transfer) — separate from, and shorter-lived than, the OTP's own entry window. */
public record StepUpVerifyResponse(boolean verified, UUID challengeId, Instant expiresAt) {
}
