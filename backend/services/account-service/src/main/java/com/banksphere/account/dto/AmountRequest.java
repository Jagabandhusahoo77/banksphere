package com.banksphere.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Shared request body for deposit and withdrawal operations.
 */
public record AmountRequest(

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "amount may have at most 4 decimal places")
        BigDecimal amount,

        String description
) {
}
