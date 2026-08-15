package com.banksphere.account.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal for an employee-originated request — set as
 * {@code Authentication.getPrincipal()} by {@link EmployeeJwtAuthenticationFilter}.
 * Deliberately a distinct type from the plain {@code String} (customer UUID)
 * principal every other endpoint in this service expects, so an employee
 * token can never be silently misread as a customer id — see
 * {@code EmployeeCurrentUser} for the one place this gets unwrapped, and
 * ADR-007 for why this whole second principal type exists.
 */
public record EmployeePrincipal(
        UUID employeeId,
        String employeeNumber,
        UUID branchId,
        String branchIfsc,
        List<String> roles,
        List<String> permissions
) {
    /** Same broader-scope roles ADR-006's RolePermissions table already treats as branch-unrestricted. */
    public boolean hasBroadBranchScope() {
        return roles.contains("BRANCH_MANAGER") || roles.contains("ADMIN");
    }
}
