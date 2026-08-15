package com.banksphere.kyc.security;

import com.banksphere.kyc.exception.WrongPrincipalTypeException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The customer-request analogue of {@link EmployeeCurrentUser}. The
 * authenticated principal's name is always the customer's UUID as a plain
 * {@code String} (set by {@link JwtAuthenticationFilter}) — this is the
 * one place that parses it back, so every customer-facing controller does
 * it the same way, and so customer identity is derived only from the
 * verified JWT, never from a request body or path variable (see
 * docs/security/authorization.md).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String customerId)) {
            // An employee token reached a customer-only endpoint — a
            // valid, verified token, just the wrong principal type for
            // this endpoint, so 403 (not 401) is correct here.
            throw new WrongPrincipalTypeException(
                    "This endpoint requires a customer identity; an employee-authenticated request cannot access it");
        }
        return UUID.fromString(customerId);
    }
}
