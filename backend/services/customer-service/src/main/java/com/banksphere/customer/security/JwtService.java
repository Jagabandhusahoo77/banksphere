package com.banksphere.customer.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates JWTs for customer-service — the only service in
 * this phase that both signs (login) and verifies (protecting its own
 * endpoints) tokens. account-service and transaction-service only ever
 * verify (see their JwtValidator), using the same shared secret, so a
 * token issued here is independently verifiable by any of the three
 * without a callback to customer-service — see docs/security/jwt.md.
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes (256 bits) for HS256 — configured value is too short");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationSeconds = properties.expirationSeconds();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public String generateToken(UUID customerId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(customerId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, List.of("ROLE_CUSTOMER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the token's claims if the signature and expiration are both
     * valid, or empty otherwise. Never throws — callers (the auth filter)
     * treat "empty" as "unauthenticated," not as a 500.
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
