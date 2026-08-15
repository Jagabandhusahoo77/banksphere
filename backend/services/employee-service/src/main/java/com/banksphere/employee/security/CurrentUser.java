package com.banksphere.employee.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The authenticated principal's name is always the employee's UUID as a
 * string (set by {@link JwtAuthenticationFilter}) — this is the one place
 * that parses it back, so every controller does it the same way. See
 * docs/security/authorization.md's rule, applied identically here: never
 * trust an id from the URL/body for an access decision — always re-derive
 * "who is calling" from the verified token.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Strips the {@code Bearer } prefix, for the one case this service
     * needs the raw token itself (Phase 9B — forwarding the acting
     * employee's own token to account-service/customer-service for their
     * employee-only endpoints, exactly as account-service already forwards
     * a customer's own token to transaction-service) rather than just the
     * identity it encodes. See AccountOperationsClient/CustomerLookupClient.
     */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
