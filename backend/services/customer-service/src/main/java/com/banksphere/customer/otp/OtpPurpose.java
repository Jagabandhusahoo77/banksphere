package com.banksphere.customer.otp;

/**
 * Only {@code LOGIN} and {@code STEP_UP_TRANSFER} are actually wired to a
 * real endpoint in Phase 9D — see ADR-009 for why {@code
 * STEP_UP_WITHDRAWAL}/{@code STEP_UP_BENEFICIARY}/{@code
 * STEP_UP_PROFILE_CHANGE} exist as real enum values (the {@code
 * StepUpPolicy} abstraction is defined for all of them) without a
 * corresponding backend enforcement call site yet — those operations
 * already exist elsewhere in the codebase, but wiring step-up into each
 * is deliberately deferred to keep this phase's scope to what its own
 * testing/E2E requirements actually exercise (transfer).
 */
public enum OtpPurpose {
    LOGIN,
    STEP_UP_TRANSFER,
    STEP_UP_WITHDRAWAL,
    STEP_UP_BENEFICIARY,
    STEP_UP_PROFILE_CHANGE
}
