package com.banksphere.beneficiary.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code banksphere.employee-jwt.secret}, sourced from
 * {@code EMPLOYEE_JWT_SECRET} — deliberately a SEPARATE property/env var
 * from {@code JwtProperties}/{@code JWT_SECRET} above, following the
 * dual-secret separation established in ADR-006/ADR-007. Phase 9C is the
 * first time beneficiary-service accepts an employee-signed token at all
 * — for the Customer 360 aggregation's beneficiaries section. See
 * ADR-008.
 */
@ConfigurationProperties(prefix = "banksphere.employee-jwt")
public record EmployeeJwtProperties(String secret) {
}
