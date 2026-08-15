package com.banksphere.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Lets an authenticated customer verify a transfer recipient — by the
 * business identifiers they'd actually be given (account number + IFSC),
 * never an internal UUID — before committing to a transfer. See ADR-005
 * for why this is a separate step from {@code POST /accounts/transfer}
 * itself (matching real bank "verify payee" UX) and why its response
 * deliberately contains no internal account id.
 */
public record ResolveRecipientRequest(

        @NotBlank(message = "accountNumber is required")
        @Pattern(regexp = "^\\d{12}$", message = "accountNumber must be exactly 12 digits")
        String accountNumber,

        @NotBlank(message = "ifsc is required")
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "ifsc must be a valid 11-character IFSC code, e.g. BANK0000001")
        String ifsc
) {
}
