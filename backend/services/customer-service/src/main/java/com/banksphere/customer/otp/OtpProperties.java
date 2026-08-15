package com.banksphere.customer.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All durations/limits are configurable — see application.yml's
 * {@code banksphere.otp} block and ADR-009's rate-limiting section.
 *
 * @param length              digits in a generated OTP (demo default 6)
 * @param expirySeconds       how long a challenge remains verifiable
 * @param maxAttempts         verification attempts before a challenge locks
 * @param resendCooldownSeconds minimum gap between two OTP requests for the same identifier+purpose
 * @param stepUpExpirySeconds how long a VERIFIED step-up challenge remains executable (separate from OTP expiry, since it's the window to complete the protected operation, not to enter the code)
 */
@ConfigurationProperties(prefix = "banksphere.otp")
public record OtpProperties(
        int length,
        long expirySeconds,
        int maxAttempts,
        long resendCooldownSeconds,
        long stepUpExpirySeconds
) {
}
