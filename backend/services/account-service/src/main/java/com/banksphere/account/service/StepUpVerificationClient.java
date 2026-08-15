package com.banksphere.account.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Confirms a step-up challenge with customer-service before a
 * step-up-protected operation executes — see ADR-009 and
 * StepUpVerificationFailedException's javadoc. Unlike {@link
 * TransactionClient} (best-effort, never rethrows), this call MUST
 * propagate failure: a failed/unreachable step-up confirmation must
 * abort the transfer, never silently let it proceed.
 */
public interface StepUpVerificationClient {

    /**
     * @throws com.banksphere.account.exception.StepUpVerificationFailedException
     *         if customer-service rejects the challenge for any reason
     *         (wrong customer, context mismatch, wrong state, expired,
     *         or the call itself fails/times out — an unreachable
     *         customer-service must never be treated as "step-up
     *         satisfied")
     */
    void confirmTransferStepUp(UUID challengeId, UUID sourceAccountId, String destinationAccountNumber,
                                String destinationIfsc, BigDecimal amount, String currency, String bearerToken);
}
