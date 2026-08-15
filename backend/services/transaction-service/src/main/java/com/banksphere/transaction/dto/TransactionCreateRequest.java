package com.banksphere.transaction.dto;

import com.banksphere.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionCreateRequest(

        @NotNull(message = "accountId is required")
        UUID accountId,

        @NotNull(message = "transactionType is required")
        TransactionType transactionType,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code, e.g. USD")
        String currency,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
