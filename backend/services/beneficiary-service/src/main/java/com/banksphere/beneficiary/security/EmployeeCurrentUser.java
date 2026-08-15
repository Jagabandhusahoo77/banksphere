package com.banksphere.beneficiary.security;

import org.springframework.security.core.Authentication;

/**
 * Unwraps the {@link EmployeePrincipal} set by {@link
 * EmployeeJwtAuthenticationFilter}. Used only by endpoints gated behind
 * {@code @PreAuthorize} against a permission no customer token can carry,
 * so the cast is safe by construction — see account-service's
 * identically-named class for the precedent.
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
