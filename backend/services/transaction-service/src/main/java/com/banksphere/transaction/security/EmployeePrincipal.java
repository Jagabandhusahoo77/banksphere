package com.banksphere.transaction.security;

import java.util.List;
import java.util.UUID;

/**
 * Phase 9C — replaces the plain {@code "employee:"}-prefixed string
 * principal this filter previously set (see {@link
 * EmployeeJwtAuthenticationFilter}'s prior javadoc) now that this service
 * has its first permission-gated employee endpoint (the Customer 360
 * transactions section, {@code TRANSACTION_VIEW} — see ADR-008). No
 * {@code branchId}/{@code branchIfsc} fields: unlike account-service's
 * cash-deposit flow, nothing in this service is branch-scoped.
 */
public record EmployeePrincipal(UUID employeeId, String employeeNumber, List<String> roles, List<String> permissions) {
}
