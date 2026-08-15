package com.banksphere.kyc.entity;

/**
 * See {@code security.KycStateMachine} (well, {@code service.KycStateMachine}
 * — this is a domain concept, not a security one) for the authoritative
 * valid-transition table. This enum only names the states; it has no
 * transition logic of its own, mirroring how {@code Role}/{@code Permission}
 * are plain enums with the actual rules living in a separate class
 * ({@code RolePermissions}) in employee-service.
 */
public enum KycStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    ADDITIONAL_INFORMATION_REQUIRED,
    RESUBMITTED,
    APPROVED,
    REJECTED
}
