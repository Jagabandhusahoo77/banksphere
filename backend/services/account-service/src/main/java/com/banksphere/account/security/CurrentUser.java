package com.banksphere.account.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The authenticated principal's name is always the customer's UUID as a
 * string (set by {@link JwtAuthenticationFilter}) — this is the one place
 * that parses it back, so every controller does it the same way.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Strips the {@code Bearer } prefix from an incoming Authorization
     * header, for the one case this service needs the raw token itself
     * (forwarding it to transaction-service — see RestTransactionClient)
     * rather than just the identity it encodes.
     */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
