package com.banksphere.account.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code banksphere.employee-jwt.secret}, sourced from
 * {@code EMPLOYEE_JWT_SECRET} — deliberately a SEPARATE property/env var
 * from {@code JwtProperties}/{@code JWT_SECRET} above. account-service now
 * accepts two structurally distinct token types (customer and employee),
 * each with its own signing key, exactly like employee-service itself
 * does not accept customer tokens (see ADR-006, Decision 2, and
 * ADR-007 for why this same separation extends to account-service).
 */
@ConfigurationProperties(prefix = "banksphere.employee-jwt")
public record EmployeeJwtProperties(String secret) {
}
