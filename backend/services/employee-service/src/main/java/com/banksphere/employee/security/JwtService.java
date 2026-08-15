package com.banksphere.employee.security;

import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.Permission;
import com.banksphere.employee.entity.Role;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issues and validates employee JWTs, signed with the employee-only key
 * derived from {@code EMPLOYEE_JWT_SECRET} (see {@link JwtProperties}).
 *
 * <p>This deliberately mirrors customer-service's {@code JwtService} shape
 * (issue + validate in one class, since employee-service is itself the
 * identity provider for employees, exactly as customer-service is for
 * customers) but is a structurally separate class with a structurally
 * separate signing key — see ADR-006, Decision 2, for why the two token
 * types must never be cryptographically interchangeable.
 *
 * <p>Claims carried, beyond the standard {@code sub}/{@code iat}/{@code exp}:
 * <ul>
 *   <li>{@code principalType = "EMPLOYEE"} — a defense-in-depth marker.
 *       The real separation is the distinct signing key (a customer JWT
 *       cannot pass signature verification here at all), but this claim
 *       means even a future bug that accidentally reused a key would still
 *       need an attacker-controlled claim to pass, not just a valid
 *       signature — see ADR-006.</li>
 *   <li>{@code employeeNumber}</li>
 *   <li>{@code roles} — role names, e.g. {@code ["TELLER"]}</li>
 *   <li>{@code permissions} — the flattened, effective permission set for
 *       those roles at issue time (see {@link RolePermissions}), so every
 *       downstream authorization check reads directly off the token
 *       without needing to re-derive it from the database on every
 *       request.</li>
 *   <li>{@code branchId} — the employee's assigned branch (internal id).</li>
 *   <li>{@code branchIfsc} — the employee's branch IFSC (Phase 9B). This is
 *       what actually makes branch-scoped authorization enforceable
 *       downstream: account-service already has {@code Account.ifsc}
 *       (Phase 8A) and never had a branch concept of its own, so rather
 *       than inventing a new account-to-branch relationship, a teller's
 *       "own branch" is verified by comparing this claim against the
 *       destination account's existing {@code ifsc} field — the two
 *       already correlate 1:1 today (one branch, one IFSC), and this is
 *       exactly the relationship a real IFSC is for. See ADR-007.</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final String CLAIM_PRINCIPAL_TYPE = "principalType";
    private static final String CLAIM_EMPLOYEE_NUMBER = "employeeNumber";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_BRANCH_ID = "branchId";
    private static final String CLAIM_BRANCH_IFSC = "branchIfsc";
    public static final String PRINCIPAL_TYPE_EMPLOYEE = "EMPLOYEE";

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "EMPLOYEE_JWT_SECRET must be at least 32 bytes (256 bits) for HS256 — configured value is too short");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationSeconds = properties.expirationSeconds();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public String generateToken(Employee employee) {
        Set<Role> roles = employee.getRoles();
        Set<Permission> permissions = RolePermissions.permissionsFor(roles);

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);
        return Jwts.builder()
                .subject(employee.getId().toString())
                .claim(CLAIM_PRINCIPAL_TYPE, PRINCIPAL_TYPE_EMPLOYEE)
                .claim(CLAIM_EMPLOYEE_NUMBER, employee.getEmployeeNumber())
                .claim(CLAIM_ROLES, roles.stream().map(Role::name).collect(Collectors.toList()))
                .claim(CLAIM_PERMISSIONS, permissions.stream().map(Permission::name).collect(Collectors.toList()))
                .claim(CLAIM_BRANCH_ID, employee.getBranch().getId().toString())
                .claim(CLAIM_BRANCH_IFSC, employee.getBranch().getIfsc())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parseClaims(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * True only for claims that both verified against this service's own key
     * AND explicitly declare themselves an employee token. A customer JWT
     * cannot reach this check at all (it fails {@link #parseClaims} first,
     * since it was signed with a different key) — this is the
     * defense-in-depth layer described in this class's own doc comment, not
     * the primary defense.
     */
    public static boolean isEmployeeToken(Claims claims) {
        return PRINCIPAL_TYPE_EMPLOYEE.equals(claims.get(CLAIM_PRINCIPAL_TYPE, String.class));
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List<?> list ? (List<String>) list.stream().map(Object::toString).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<String> permissions(Claims claims) {
        Object permissions = claims.get(CLAIM_PERMISSIONS);
        return permissions instanceof List<?> list ? (List<String>) list.stream().map(Object::toString).toList() : List.of();
    }

    public static String employeeNumber(Claims claims) {
        return claims.get(CLAIM_EMPLOYEE_NUMBER, String.class);
    }

    public static String branchId(Claims claims) {
        return claims.get(CLAIM_BRANCH_ID, String.class);
    }

    public static String branchIfsc(Claims claims) {
        return claims.get(CLAIM_BRANCH_IFSC, String.class);
    }
}
