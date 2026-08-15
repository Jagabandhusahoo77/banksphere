package com.banksphere.customer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code EMPLOYEE_JWT_SECRET} — see account-service's identically-purposed class and ADR-007. */
@ConfigurationProperties(prefix = "banksphere.employee-jwt")
public record EmployeeJwtProperties(String secret) {
}
