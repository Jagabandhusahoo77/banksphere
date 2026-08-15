package com.banksphere.customer.security;

import org.springframework.security.core.Authentication;

/** See account-service's identically-purposed class and ADR-007. */
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
