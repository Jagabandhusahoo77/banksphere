package com.banksphere.kyc.security;

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
 * Validates JWTs issued by employee-service, using {@code EMPLOYEE_JWT_SECRET}
 * — a second, cryptographically distinct instance of the same stateless
 * verification pattern {@link JwtValidator} uses for customer-service's
 * tokens. A token signed with the customer {@code JWT_SECRET} fails
 * verification here outright (wrong key).
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
