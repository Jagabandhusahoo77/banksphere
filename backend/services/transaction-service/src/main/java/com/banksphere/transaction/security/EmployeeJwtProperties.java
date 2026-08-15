package com.banksphere.transaction.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code EMPLOYEE_JWT_SECRET} — see account-service's
 * identically-purposed class and ADR-007. transaction-service needs this
 * only so {@code POST /api/v1/transactions} recognizes an employee token
 * as "authenticated" (that endpoint has never checked identity beyond
 * that — see ADR-001) when account-service forwards one on behalf of an
 * employee-initiated cash deposit.
 */
@ConfigurationProperties(prefix = "banksphere.employee-jwt")
public record EmployeeJwtProperties(String secret) {
}
