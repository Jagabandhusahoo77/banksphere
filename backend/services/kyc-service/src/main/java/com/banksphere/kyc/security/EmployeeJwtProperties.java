package com.banksphere.kyc.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code banksphere.employee-jwt.secret}, sourced from
 * {@code EMPLOYEE_JWT_SECRET} — deliberately a SEPARATE property/env var
 * from {@code JwtProperties}/{@code JWT_SECRET} above, following the exact
 * dual-secret separation established in ADR-006/ADR-007 and already
 * reused by account-service, transaction-service, and customer-service in
 * Phase 9B. kyc-service is the first service where this separation gates
 * two genuinely distinct, first-class API surfaces
 * ({@code /api/v1/kyc/applications/**} vs {@code /api/v1/kyc/employee/**})
 * rather than a single endpoint accepting either principal type.
 */
@ConfigurationProperties(prefix = "banksphere.employee-jwt")
public record EmployeeJwtProperties(String secret) {
}
