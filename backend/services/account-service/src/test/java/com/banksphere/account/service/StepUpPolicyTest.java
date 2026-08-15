package com.banksphere.account.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenario 28 ("step-up required per policy") at the policy-decision level — see ADR-009. */
class StepUpPolicyTest {

    private final StepUpPolicy policy = new StepUpPolicy(new StepUpPolicy.Properties(new BigDecimal("50000.00")));

    @Test
    void requiresStepUpForTransfer_isFalse_belowTheThreshold() {
        assertThat(policy.requiresStepUpForTransfer(new BigDecimal("49999.99"))).isFalse();
    }

    @Test
    void requiresStepUpForTransfer_isTrue_atExactlyTheThreshold() {
        assertThat(policy.requiresStepUpForTransfer(new BigDecimal("50000.00"))).isTrue();
    }

    @Test
    void requiresStepUpForTransfer_isTrue_aboveTheThreshold() {
        assertThat(policy.requiresStepUpForTransfer(new BigDecimal("100000.00"))).isTrue();
    }

    @Test
    void requiresStepUpForTransfer_isScaleInsensitive() {
        // 50000.0000 must compare equal to the 50000.00 threshold — BigDecimal.compareTo
        // (not equals) is what the policy uses, exactly for this reason.
        assertThat(policy.requiresStepUpForTransfer(new BigDecimal("50000.0000"))).isTrue();
    }

    @Test
    void requiresStepUpForWithdrawal_isDefinedButAlwaysTrue_notYetPolicyTunable() {
        assertThat(policy.requiresStepUpForWithdrawal()).isTrue();
    }

    @Test
    void requiresStepUpForBeneficiaryCreation_isDefinedButAlwaysTrue_notYetPolicyTunable() {
        assertThat(policy.requiresStepUpForBeneficiaryCreation()).isTrue();
    }
}
