package com.banksphere.customer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code secret} must never be hard-coded or committed — it comes from the
 * {@code JWT_SECRET} environment variable (see application.yml). The
 * default in application.yml is a clearly-labeled local-dev-only value,
 * the same pattern already used for DB_PASSWORD.
 */
@ConfigurationProperties(prefix = "banksphere.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
