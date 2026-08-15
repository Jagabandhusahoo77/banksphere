package com.banksphere.transaction.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * transaction-service only ever validates JWTs (never issues them — see
 * customer-service's JwtService). {@code secret} must match
 * customer-service's exactly (same env var, {@code JWT_SECRET}).
 */
@ConfigurationProperties(prefix = "banksphere.jwt")
public record JwtProperties(String secret) {
}
