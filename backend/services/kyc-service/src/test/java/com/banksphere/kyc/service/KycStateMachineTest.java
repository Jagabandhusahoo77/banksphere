package com.banksphere.kyc.service;

import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.banksphere.kyc.entity.KycStatus.ADDITIONAL_INFORMATION_REQUIRED;
import static com.banksphere.kyc.entity.KycStatus.APPROVED;
import static com.banksphere.kyc.entity.KycStatus.DRAFT;
import static com.banksphere.kyc.entity.KycStatus.REJECTED;
import static com.banksphere.kyc.entity.KycStatus.RESUBMITTED;
import static com.banksphere.kyc.entity.KycStatus.SUBMITTED;
import static com.banksphere.kyc.entity.KycStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class KycStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT, SUBMITTED",
            "SUBMITTED, UNDER_REVIEW",
            "UNDER_REVIEW, APPROVED",
            "UNDER_REVIEW, REJECTED",
            "UNDER_REVIEW, ADDITIONAL_INFORMATION_REQUIRED",
            "ADDITIONAL_INFORMATION_REQUIRED, RESUBMITTED",
            "RESUBMITTED, UNDER_REVIEW",
    })
    void isValidTransition_returnsTrue_forEveryLockedInTransition(KycStatus from, KycStatus to) {
        assertThat(KycStateMachine.isValidTransition(from, to)).isTrue();
        assertThatNoException().isThrownBy(() -> KycStateMachine.requireValidTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, DRAFT",
            "APPROVED, REJECTED",
            "REJECTED, APPROVED",
            "DRAFT, UNDER_REVIEW",
            "DRAFT, APPROVED",
            "SUBMITTED, APPROVED",
            "SUBMITTED, DRAFT",
    })
    void requireValidTransition_throws_forEveryDisallowedTransition(KycStatus from, KycStatus to) {
        assertThatThrownBy(() -> KycStateMachine.requireValidTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void approvedAndRejected_areBothTerminal_withNoOutgoingTransitions() {
        for (KycStatus to : KycStatus.values()) {
            assertThat(KycStateMachine.isValidTransition(APPROVED, to)).isFalse();
            assertThat(KycStateMachine.isValidTransition(REJECTED, to)).isFalse();
        }
    }
}
