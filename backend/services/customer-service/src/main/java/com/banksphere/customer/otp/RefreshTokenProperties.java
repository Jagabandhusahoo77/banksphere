package com.banksphere.customer.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param expirySeconds default 14 days — long-lived relative to the access token's 1-hour default, see ADR-009. */
@ConfigurationProperties(prefix = "banksphere.refresh-token")
public record RefreshTokenProperties(long expirySeconds, boolean cookieSecure) {
}
