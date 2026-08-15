package com.banksphere.kyc.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal for an employee-originated request — set as
 * {@code Authentication.getPrincipal()} by {@link EmployeeJwtAuthenticationFilter}.
 * Deliberately a distinct type from the plain {@code String} (customer
 * UUID) principal {@link JwtAuthenticationFilter} sets, so an employee
 * token can never be silently misread as a customer id — see
 * {@link EmployeeCurrentUser} for the one place this gets unwrapped, and
 * {@link CurrentUser#id} for the customer-side guard against the reverse
 * mistake.
 *
 * <p>{@code branchId}/{@code branchIfsc} are carried for audit-trail
 * completeness only — KYC review is NOT branch-scoped (see ADR-008's
 * branch-scope decision: unlike {@code Account.ifsc} for cash deposit, a
 * KYC application has no natural branch anchor, so none was invented).
 */
public record EmployeePrincipal(
        UUID employeeId,
        String employeeNumber,
        UUID branchId,
        String branchIfsc,
        List<String> roles,
        List<String> permissions
) {
}
