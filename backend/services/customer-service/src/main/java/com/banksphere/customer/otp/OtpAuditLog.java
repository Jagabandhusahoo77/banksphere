package com.banksphere.customer.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured, single-line audit events for OTP/step-up authentication —
 * mirrors {@code EmployeeAuditLog}/{@code KycAuditLog}'s established
 * shape exactly (dedicated logger, fixed key=value fields, correlation
 * id from {@link CorrelationIdFilter}'s MDC entry). Not the real Audit
 * Service — no Kafka, no new external system — see ADR-009.
 *
 * <p><b>Never logs</b>: the OTP value itself, a password, a refresh
 * token, or an access token — every method here takes a {@code
 * challengeId}/{@code customerId}, never the secret those ids protect.
 * {@code identifier} is masked to its last 4 characters wherever it
 * appears, the same masking convention {@code BeneficiaryServiceImpl}
 * already uses for account numbers.
 */
@Component
public class OtpAuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.banksphere.customer.AUDIT");

    public void otpRequested(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId) {
        log("OTP_REQUESTED", identifier, purpose, challengeId, customerId, "SUCCESS");
    }

    public void otpDelivered(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId) {
        log("OTP_DELIVERED", identifier, purpose, challengeId, customerId, "SUCCESS");
    }

    public void otpVerificationSucceeded(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId) {
        log("OTP_VERIFICATION_SUCCEEDED", identifier, purpose, challengeId, customerId, "SUCCESS");
    }

    public void otpVerificationFailed(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId, String reason) {
        log("OTP_VERIFICATION_FAILED", identifier, purpose, challengeId, customerId, "FAILURE(reason=" + reason + ")");
    }

    public void otpExpired(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId) {
        log("OTP_EXPIRED", identifier, purpose, challengeId, customerId, "FAILURE");
    }

    public void otpLocked(String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId) {
        log("OTP_LOCKED", identifier, purpose, challengeId, customerId, "FAILURE(reason=max attempts exceeded)");
    }

    public void stepUpRequested(UUID customerId, OtpPurpose purpose, UUID challengeId) {
        log("STEP_UP_REQUESTED", null, purpose, challengeId, customerId, "SUCCESS");
    }

    public void stepUpVerified(UUID customerId, OtpPurpose purpose, UUID challengeId) {
        log("STEP_UP_VERIFIED", null, purpose, challengeId, customerId, "SUCCESS");
    }

    public void stepUpFailed(UUID customerId, OtpPurpose purpose, UUID challengeId, String reason) {
        log("STEP_UP_FAILED", null, purpose, challengeId, customerId, "FAILURE(reason=" + reason + ")");
    }

    public void stepUpExpired(UUID customerId, OtpPurpose purpose, UUID challengeId) {
        log("STEP_UP_EXPIRED", null, purpose, challengeId, customerId, "FAILURE");
    }

    public void stepUpExecuted(UUID customerId, OtpPurpose purpose, UUID challengeId) {
        log("STEP_UP_EXECUTED", null, purpose, challengeId, customerId, "SUCCESS");
    }

    public void refreshTokenReuseDetected(UUID customerId, UUID tokenId) {
        String correlationId = MDC.get("correlationId");
        AUDIT.warn("action=REFRESH_TOKEN_REUSE_DETECTED customerId={} tokenId={} result=FAILURE(reason=possible token theft, all sessions revoked) timestamp={} correlationId={}",
                customerId, tokenId, Instant.now(), correlationId);
    }

    private void log(String action, String identifier, OtpPurpose purpose, UUID challengeId, UUID customerId, String result) {
        String correlationId = MDC.get("correlationId");
        AUDIT.info("action={} identifier={} purpose={} challengeId={} customerId={} result={} timestamp={} correlationId={}",
                action, mask(identifier), purpose, challengeId, customerId, result, Instant.now(), correlationId);
    }

    private String mask(String identifier) {
        if (identifier == null) {
            return null;
        }
        if (identifier.length() <= 4) {
            return "****";
        }
        return "****" + identifier.substring(identifier.length() - 4);
    }
}
