package com.banksphere.transaction.dto;

import com.banksphere.transaction.entity.Transaction;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String transactionReference,
        UUID accountId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String description,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getAccountId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
