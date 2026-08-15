package com.banksphere.account.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Validates JWTs issued by employee-service, using the same
 * {@code EMPLOYEE_JWT_SECRET} employee-service itself signs with —
 * structurally the same "downstream service independently re-verifies the
 * caller's real token" pattern already used for {@link JwtValidator}
 * verifying customer-service's tokens; this is just a second instance of
 * it for a second principal type. A token signed with the customer
 * {@code JWT_SECRET} fails verification here outright (wrong key) — see
 * ADR-007.
 */
@Component
@EnableConfigurationProperties(EmployeeJwtProperties.class)
public class EmployeeJwtValidator {

    private final SecretKey key;

    public EmployeeJwtValidator(EmployeeJwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "EMPLOYEE_JWT_SECRET must be at least 32 bytes (256 bits) for HS256 — configured value is too short");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
