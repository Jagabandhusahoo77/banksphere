package com.banksphere.beneficiary.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The authenticated principal's name is always the customer's UUID as a
 * string (set by {@link JwtAuthenticationFilter}) — this is the one place
 * that parses it back, so every controller does it the same way.
 *
 * <p>Unlike account-service/transaction-service's {@code CurrentUser},
 * this one has no {@code bearerToken()} helper: beneficiary-service makes
 * no outbound calls to other services in this phase (see
 * docs/architecture/microservices.md), so there's nothing to forward a
 * token to yet.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
