package com.banksphere.customer.otp;

/**
 * {@code PENDING} → {@code VERIFIED} (OTP entered correctly; only
 * meaningful for a step-up purpose — a {@code LOGIN} challenge goes
 * straight to {@code CONSUMED}, since login has nothing further to
 * authorize) → {@code EXECUTED} (the downstream operation the step-up
 * protected has actually happened — set by the internal confirm call, see
 * OtpServiceImpl#confirmStepUpExecution). {@code EXPIRED}/{@code LOCKED}
 * are terminal failure states; {@code CONSUMED} is LOGIN's terminal
 * success state.
 */
public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXECUTED,
    EXPIRED,
    LOCKED,
    CONSUMED
}
