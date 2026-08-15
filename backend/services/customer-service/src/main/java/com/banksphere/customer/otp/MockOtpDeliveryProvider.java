package com.banksphere.customer.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The only {@link OtpDeliveryProvider} implementation this phase builds —
 * no SMS/email/WhatsApp integration, no external provider, nothing paid.
 * "Delivery" here means a structured console log line — one of the
 * task's own sanctioned local-development retrieval mechanisms, alongside
 * {@link DevOtpInbox} (populated separately by {@code OtpServiceImpl},
 * not by this class — see its own javadoc for why). This class carries
 * the real OTP value in its log line, which is acceptable ONLY because
 * this entire codebase is a fictional, local-development-only project
 * with no real deployment — a real {@code SmsOtpDeliveryProvider}/{@code
 * EmailOtpDeliveryProvider} would never log the code at all.
 */
@Component
public class MockOtpDeliveryProvider implements OtpDeliveryProvider {

    private static final Logger log = LoggerFactory.getLogger(MockOtpDeliveryProvider.class);

    @Override
    public void deliver(String identifier, String otp, OtpPurpose purpose) {
        log.info("[MOCK OTP DELIVERY] purpose={} identifier={} otp={}", purpose, maskIdentifier(identifier), otp);
    }

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 4) {
            return "****";
        }
        return "****" + identifier.substring(identifier.length() - 4);
    }
}
