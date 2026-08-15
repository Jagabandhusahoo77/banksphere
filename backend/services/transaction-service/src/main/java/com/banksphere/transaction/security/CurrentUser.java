package com.banksphere.transaction.security;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static final String BEARER_PREFIX = "Bearer ";

    /** Strips the {@code Bearer } prefix, for forwarding the caller's own token to account-service's ownership check. */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
