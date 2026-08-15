package com.banksphere.customer.otp;

public enum RefreshTokenStatus {
    ACTIVE,
    /** Rotated out by a successful {@code /auth/token/refresh} call — its replacement is {@code replacedByTokenId}. */
    ROTATED,
    /** Explicitly revoked — either a real logout, or reuse-detection revoking an entire token family. */
    REVOKED
}
