package com.banksphere.customer.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code enabled} gates whether {@link DevOtpInboxController} even exists
 * in the application context (see its {@code @ConditionalOnProperty}) —
 * not just whether it returns data. When disabled, the route 404s via
 * Spring's ordinary "no handler found," not a runtime permission check
 * that could be misconfigured — see ADR-009. Defaults to enabled for
 * local Docker Compose development, the same convention as every other
 * local-dev-only default in this codebase (DB password, JWT secret); this
 * MUST be set to {@code false} (or the whole feature simply not deployed)
 * in any real environment.
 */
@ConfigurationProperties(prefix = "banksphere.otp.dev-inbox")
public record DevOtpInboxProperties(boolean enabled) {
}
