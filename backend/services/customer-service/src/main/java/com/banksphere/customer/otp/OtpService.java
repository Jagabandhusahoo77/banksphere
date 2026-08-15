package com.banksphere.customer.otp;

import com.banksphere.customer.otp.dto.OtpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpConfirmRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmResponse;
import com.banksphere.customer.otp.dto.StepUpRequestRequest;
import com.banksphere.customer.otp.dto.StepUpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpVerifyResponse;

import java.util.UUID;

/** See ADR-009 for the full lifecycle/security design this interface implements. */
public interface OtpService {

    /** Always returns a generic response and a real challengeId, whether or not {@code identifier} matches a real customer. */
    OtpRequestResponse requestLoginOtp(String identifier, String clientIp);

    /** Throws {@link com.banksphere.customer.exception.InvalidOtpException}/{@link com.banksphere.customer.exception.OtpChallengeNotFoundException} on any failure — never distinguishes the reason in the response. */
    OtpVerificationResult verifyLoginOtp(UUID challengeId, String otp, String clientIp);

    /** Requires an authenticated customer — {@code customerId} always comes from the caller's own verified JWT, never the request body. */
    StepUpRequestResponse requestStepUp(UUID customerId, StepUpRequestRequest request, String clientIp);

    StepUpVerifyResponse verifyStepUp(UUID customerId, UUID challengeId, String otp, String clientIp);

    /**
     * Called by a downstream service (e.g. account-service) forwarding
     * the customer's own bearer token — confirms a step-up challenge is
     * {@code VERIFIED}, belongs to {@code customerId}, matches {@code
     * request}'s operation context exactly, and atomically transitions it
     * to {@code EXECUTED} so it can never authorize a second operation.
     */
    StepUpConfirmResponse confirmStepUpExecution(UUID customerId, StepUpConfirmRequest request);
}
