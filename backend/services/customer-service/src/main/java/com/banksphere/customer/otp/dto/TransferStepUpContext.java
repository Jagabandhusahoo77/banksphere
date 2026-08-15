package com.banksphere.customer.otp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The exact operation fields a {@code STEP_UP_TRANSFER} challenge is
 * bound to — deliberately the same shape as account-service's own {@code
 * TransferRequest} (minus {@code description}, which isn't
 * security-relevant and so isn't part of what step-up authorizes). See
 * {@code OtpContextHasher} and ADR-009's operation-binding section.
 */
public record TransferStepUpContext(
        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotBlank(message = "destinationAccountNumber is required")
        String destinationAccountNumber,

        @NotBlank(message = "destinationIfsc is required")
        String destinationIfsc,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency
) {
    /** The canonical, order-fixed field list {@link com.banksphere.customer.otp.OtpContextHasher} hashes — must match account-service's own recomputation exactly. */
    public List<String> canonicalParts() {
        return List.of(
                sourceAccountId.toString(),
                destinationAccountNumber,
                destinationIfsc,
                amount.stripTrailingZeros().toPlainString(),
                currency);
    }
}
