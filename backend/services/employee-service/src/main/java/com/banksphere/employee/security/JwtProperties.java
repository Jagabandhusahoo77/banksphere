package com.banksphere.employee.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code banksphere.jwt.*}, sourced from the {@code EMPLOYEE_JWT_SECRET}
 * / {@code EMPLOYEE_JWT_EXPIRATION} env vars — deliberately NOT {@code JWT_SECRET}.
 * See ADR-006, Decision 2: employee-service both issues and validates its own
 * tokens with its own key, so this record carries {@code expirationSeconds}
 * (unlike account/beneficiary/transaction-service's validate-only
 * {@code JwtProperties}, which never needs a TTL since they never issue a token).
 */
@ConfigurationProperties(prefix = "banksphere.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
