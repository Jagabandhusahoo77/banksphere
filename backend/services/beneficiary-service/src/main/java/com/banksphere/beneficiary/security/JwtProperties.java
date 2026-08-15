package com.banksphere.beneficiary.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * beneficiary-service only ever validates JWTs (never issues them — see
 * customer-service's JwtService for that) so it only needs the shared
 * signing secret, not an expiration policy. {@code secret} must match
 * customer-service's exactly (same env var, {@code JWT_SECRET}) or every
 * token this service receives will fail signature verification.
 */
@ConfigurationProperties(prefix = "banksphere.jwt")
public record JwtProperties(String secret) {
}
