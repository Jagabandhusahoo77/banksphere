package com.banksphere.kyc.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * kyc-service only ever validates customer JWTs (never issues them — see
 * customer-service's JwtService for that). {@code secret} must match
 * customer-service's exactly (same env var, {@code JWT_SECRET}) or every
 * customer token this service receives will fail signature verification.
 */
@ConfigurationProperties(prefix = "banksphere.jwt")
public record JwtProperties(String secret) {
}
