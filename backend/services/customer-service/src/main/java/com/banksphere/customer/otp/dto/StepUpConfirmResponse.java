package com.banksphere.customer.otp.dto;

import java.util.UUID;

public record StepUpConfirmResponse(boolean confirmed, UUID challengeId) {
}
