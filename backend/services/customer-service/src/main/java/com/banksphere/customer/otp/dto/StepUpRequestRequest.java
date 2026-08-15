package com.banksphere.customer.otp.dto;

import com.banksphere.customer.otp.OtpPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code transferContext} is required if and only if {@code purpose ==
 * STEP_UP_TRANSFER} — validated in {@code OtpServiceImpl}, not via bean
 * validation, since it's a cross-field rule. Only {@code STEP_UP_TRANSFER}
 * is accepted this phase (see {@link OtpPurpose}'s javadoc) — any other
 * {@code STEP_UP_*} purpose is rejected with {@code 400} rather than
 * silently accepted with no context to bind to.
 */
public record StepUpRequestRequest(
        @NotNull(message = "purpose is required")
        OtpPurpose purpose,

        @Valid
        TransferStepUpContext transferContext
) {
}
