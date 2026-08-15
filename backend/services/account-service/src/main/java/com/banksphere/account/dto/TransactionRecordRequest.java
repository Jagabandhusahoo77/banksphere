package com.banksphere.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload sent to transaction-service to record a ledger entry for a
 * deposit or withdrawal performed on this account.
 */
public record TransactionRecordRequest(
        UUID accountId,
        String transactionType,
        BigDecimal amount,
        String currency,
        String description
) {
}
