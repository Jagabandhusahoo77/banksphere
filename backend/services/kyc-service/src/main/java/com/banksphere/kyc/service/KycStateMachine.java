package com.banksphere.kyc.service;

import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.banksphere.kyc.entity.KycStatus.ADDITIONAL_INFORMATION_REQUIRED;
import static com.banksphere.kyc.entity.KycStatus.APPROVED;
import static com.banksphere.kyc.entity.KycStatus.DRAFT;
import static com.banksphere.kyc.entity.KycStatus.REJECTED;
import static com.banksphere.kyc.entity.KycStatus.RESUBMITTED;
import static com.banksphere.kyc.entity.KycStatus.SUBMITTED;
import static com.banksphere.kyc.entity.KycStatus.UNDER_REVIEW;

/**
 * The authoritative KYC valid-transition table, mirroring the exact
 * diagram this phase's task specified — see ADR-008. Every mutation that
 * changes {@code KycApplication.status} must go through {@link
 * #requireValidTransition}, so an invalid transition (e.g. {@code
 * APPROVED -> DRAFT}, {@code APPROVED -> REJECTED}, {@code REJECTED ->
 * APPROVED}) is rejected with a {@code 422} regardless of which endpoint
 * or service-layer code path attempted it — the same "one authoritative
 * table, never scattered ad hoc checks" pattern {@code RolePermissions}
 * already established in employee-service.
 */
public final class KycStateMachine {

    private static final Map<KycStatus, Set<KycStatus>> VALID_TRANSITIONS = new EnumMap<>(KycStatus.class);

    static {
        VALID_TRANSITIONS.put(DRAFT, EnumSet.of(SUBMITTED));
        VALID_TRANSITIONS.put(SUBMITTED, EnumSet.of(UNDER_REVIEW));
        VALID_TRANSITIONS.put(UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, ADDITIONAL_INFORMATION_REQUIRED));
        VALID_TRANSITIONS.put(ADDITIONAL_INFORMATION_REQUIRED, EnumSet.of(RESUBMITTED));
        VALID_TRANSITIONS.put(RESUBMITTED, EnumSet.of(UNDER_REVIEW));
        VALID_TRANSITIONS.put(APPROVED, EnumSet.noneOf(KycStatus.class));
        VALID_TRANSITIONS.put(REJECTED, EnumSet.noneOf(KycStatus.class));
    }

    private KycStateMachine() {
    }

    public static boolean isValidTransition(KycStatus from, KycStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireValidTransition(KycStatus from, KycStatus to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }
}
