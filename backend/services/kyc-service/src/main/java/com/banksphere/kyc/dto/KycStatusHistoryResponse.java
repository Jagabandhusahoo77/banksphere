package com.banksphere.kyc.dto;

import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.entity.KycStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record KycStatusHistoryResponse(
        KycStatus fromStatus,
        KycStatus toStatus,
        UUID changedByEmployeeId,
        String reason,
        Instant changedAt
) {
    public static KycStatusHistoryResponse from(KycStatusHistory history) {
        return new KycStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedByEmployeeId(),
                history.getReason(),
                history.getChangedAt());
    }
}
