package com.banksphere.account.security;

import org.springframework.security.core.Authentication;

/**
 * The employee-request analogue of {@link CurrentUser} — unwraps the
 * {@link EmployeePrincipal} set by {@link EmployeeJwtAuthenticationFilter}.
 * Used only by the small set of endpoints that are exclusively reachable
 * by an employee token (gated by {@code @PreAuthorize} against a
 * permission no customer token can carry), so the cast below is safe by
 * construction, not by hope.
 */
public final class EmployeeCurrentUser {

    private EmployeeCurrentUser() {
    }

    public static EmployeePrincipal identity(Authentication authentication) {
        if (authentication.getPrincipal() instanceof EmployeePrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("Expected an employee-authenticated request but principal was: "
                + authentication.getPrincipal().getClass());
    }
}
