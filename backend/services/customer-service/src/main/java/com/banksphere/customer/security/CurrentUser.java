package com.banksphere.customer.security;

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
}
