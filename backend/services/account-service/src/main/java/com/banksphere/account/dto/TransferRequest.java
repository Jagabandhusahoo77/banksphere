package com.banksphere.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code customerId} is deliberately NOT a field here, for the same reason
 * as {@link AccountCreateRequest}: the transferring customer is always the
 * authenticated caller, taken from the JWT in AccountController — never
 * trust an id supplied by the browser for a sensitive operation.
 * <p>
 * The destination is identified by {@code destinationAccountNumber} +
 * {@code destinationIfsc} — the same business identifiers a real customer
 * actually knows — not an internal {@code destinationAccountId} UUID
 * (Phase 7A's original shape). See ADR-005: the destination account's
 * internal id is resolved server-side in {@code AccountServiceImpl} and
 * never appears in this request or in {@link TransferResponse}.
 * <p>
 * Phase 9D — {@code stepUpChallengeId} is required only when {@code
 * StepUpPolicy.requiresStepUpForTransfer(amount)} is true (see
 * {@code AccountServiceImpl.transfer}) — a customer-service step-up
 * challenge id the caller obtained by requesting and verifying an OTP for
 * this exact operation first. {@code idempotencyKey} is optional but
 * strongly recommended by every real client: an opaque, client-generated
 * string scoped per-customer that makes a retried/duplicated request
 * (double-click, network retry, browser refresh) return the original
 * result instead of executing a second transfer — see ADR-009's
 * idempotency section.
 */
public record TransferRequest(

        @NotNull(message = "sourceAccountId is required")
        UUID sourceAccountId,

        @NotBlank(message = "destinationAccountNumber is required")
        @Pattern(regexp = "^\\d{12}$", message = "destinationAccountNumber must be exactly 12 digits")
        String destinationAccountNumber,

        @NotBlank(message = "destinationIfsc is required")
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "destinationIfsc must be a valid 11-character IFSC code, e.g. BANK0000001")
        String destinationIfsc,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", inclusive = true, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "amount may have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        UUID stepUpChallengeId,

        @Size(max = 100, message = "idempotencyKey must be at most 100 characters")
        String idempotencyKey
) {
}
