package com.banksphere.kyc.dto;

import com.banksphere.kyc.entity.KycStatus;

import java.time.Instant;
import java.util.UUID;

public record KycQueueItemResponse(
        UUID applicationId,
        UUID customerId,
        Instant submittedAt,
        KycStatus status,
        int documentsSubmitted,
        int documentsRequired,
        UUID currentReviewerId
) {
}
