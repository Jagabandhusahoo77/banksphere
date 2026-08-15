package com.banksphere.customer.otp.dto;

import java.time.Instant;
import java.util.UUID;

public record StepUpRequestResponse(UUID challengeId, Instant expiresAt) {
}
