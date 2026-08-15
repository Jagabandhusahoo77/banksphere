package com.banksphere.account.dto;

import java.util.UUID;

public record StepUpConfirmResponse(boolean confirmed, UUID challengeId) {
}
