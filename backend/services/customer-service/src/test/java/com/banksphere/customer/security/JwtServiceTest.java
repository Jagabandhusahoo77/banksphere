package com.banksphere.customer.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-jwt-signing-secret-at-least-32-bytes-long-1234567890";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600));

    @Test
    void generateToken_producesTokenWhoseClaimsRoundTripCorrectly() {
        UUID customerId = UUID.randomUUID();

        String token = jwtService.generateToken(customerId, "jane.doe@example.com");
        Optional<Claims> claims = jwtService.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo(customerId.toString());
        assertThat(claims.get().get("email")).isEqualTo("jane.doe@example.com");
        assertThat(claims.get().get("roles")).isEqualTo(java.util.List.of("ROLE_CUSTOMER"));
        assertThat(claims.get().getExpiration()).isAfter(new Date());
    }

    @Test
    void parseClaims_returnsEmpty_forMalformedToken() {
        assertThat(jwtService.parseClaims("not-a-real-jwt")).isEmpty();
    }

    @Test
    void parseClaims_returnsEmpty_forTokenSignedWithADifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-signing-secret-1234567890ab".getBytes(StandardCharsets.UTF_8));
        String tokenSignedByAnotherIssuer = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.parseClaims(tokenSignedByAnotherIssuer)).isEmpty();
    }

    @Test
    void parseClaims_returnsEmpty_forExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600))) // expired an hour ago
                .signWith(key)
                .compact();

        assertThat(jwtService.parseClaims(expiredToken)).isEmpty();
    }

    @Test
    void constructor_rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be at least 32 bytes");
    }
}
