package com.banksphere.employee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deliberately does NOT accept {@code customerId}, {@code employeeId}, or
 * {@code branchId} — the acting employee's identity and branch always
 * come from their own verified JWT ({@code CurrentUser}/{@code
 * Employee} lookup), never from this request body; the account's owning
 * customer is derived server-side from {@code accountId} via
 * account-service, never asserted by the caller. See ADR-007.
 */
public record CashDepositRequest(
        @NotNull(message = "accountId is required")
        UUID accountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "amount may have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 300, message = "description must be at most 300 characters")
        String description
) {
}
