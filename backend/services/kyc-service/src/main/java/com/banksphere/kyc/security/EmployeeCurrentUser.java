package com.banksphere.kyc.security;

import org.springframework.security.core.Authentication;

/**
 * The employee-request analogue of {@link CurrentUser} — unwraps the
 * {@link EmployeePrincipal} set by {@link EmployeeJwtAuthenticationFilter}.
 * Used only by {@code /api/v1/kyc/employee/**} endpoints, every one of
 * which is gated by {@code @PreAuthorize} against a {@code KYC_*}
 * permission no customer token can carry — so a customer token is
 * rejected with {@code 403} before ever reaching this cast, and the cast
 * itself is safe by construction here.
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
