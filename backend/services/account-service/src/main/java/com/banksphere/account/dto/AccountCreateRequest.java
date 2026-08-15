package com.banksphere.account.dto;

import com.banksphere.account.entity.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * {@code customerId} is deliberately NOT a field here (Phase 3A change —
 * see docs/security/authorization.md). Before authentication existed, the
 * caller supplied it directly, which meant nothing stopped a client from
 * opening an account "owned by" an arbitrary customer id. The owner is
 * now always the authenticated caller, taken from the JWT in
 * AccountController — never trust an id supplied by the browser for a
 * sensitive operation.
 */
public record AccountCreateRequest(

        @NotNull(message = "accountType is required")
        AccountType accountType,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code, e.g. USD")
        String currency,

        @DecimalMin(value = "0.0", inclusive = true, message = "initialDeposit must not be negative")
        BigDecimal initialDeposit
) {
}
