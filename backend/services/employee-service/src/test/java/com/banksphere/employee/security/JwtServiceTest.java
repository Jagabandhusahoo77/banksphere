package com.banksphere.employee.security;

import com.banksphere.employee.entity.Branch;
import com.banksphere.employee.entity.BranchStatus;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-EMPLOYEE-jwt-signing-secret-at-least-32-bytes-1234567890";
    private static final String CUSTOMER_SECRET = "test-only-CUSTOMER-jwt-signing-secret-completely-different-abcdef";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600));

    private Employee sampleEmployee() {
        Branch branch = Branch.builder()
                .id(UUID.randomUUID())
                .branchCode("HQ001")
                .branchName("BankSphere Head Office")
                .ifsc("BANK0000001")
                .status(BranchStatus.ACTIVE)
                .build();
        return Employee.builder()
                .id(UUID.randomUUID())
                .employeeNumber("EMP000123")
                .username("jane.teller")
                .passwordHash("irrelevant-for-token-generation")
                .firstName("Jane")
                .lastName("Teller")
                .email("jane.teller@banksphere.example")
                .branch(branch)
                .status(EmployeeStatus.ACTIVE)
                .roles(Set.of(Role.TELLER))
                .build();
    }

    @Test
    void generateToken_producesTokenWhoseClaimsRoundTripCorrectly() {
        Employee employee = sampleEmployee();

        String token = jwtService.generateToken(employee);
        Optional<Claims> claims = jwtService.parseClaims(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo(employee.getId().toString());
        assertThat(JwtService.isEmployeeToken(claims.get())).isTrue();
        assertThat(JwtService.employeeNumber(claims.get())).isEqualTo("EMP000123");
        assertThat(JwtService.roles(claims.get())).containsExactly("TELLER");
        assertThat(JwtService.permissions(claims.get())).containsExactlyInAnyOrder(
                "CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL");
        assertThat(JwtService.branchId(claims.get())).isEqualTo(employee.getBranch().getId().toString());
        assertThat(JwtService.branchIfsc(claims.get())).isEqualTo("BANK0000001");
        assertThat(claims.get().getExpiration()).isAfter(new Date());
    }

    @Test
    void generateToken_forEmployeeWithMultipleRoles_unionsPermissionsIntoOneClaim() {
        Employee employee = sampleEmployee();
        employee.setRoles(Set.of(Role.TELLER, Role.KYC_OFFICER));

        Claims claims = jwtService.parseClaims(jwtService.generateToken(employee)).orElseThrow();

        assertThat(JwtService.roles(claims)).containsExactlyInAnyOrder("TELLER", "KYC_OFFICER");
        assertThat(JwtService.permissions(claims)).containsExactlyInAnyOrder(
                "CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL",
                "KYC_VIEW", "KYC_REVIEW", "KYC_APPROVE", "KYC_REJECT");
    }

    @Test
    void parseClaims_returnsEmpty_forMalformedToken() {
        assertThat(jwtService.parseClaims("not-a-real-jwt")).isEmpty();
    }

    /**
     * The core proof behind "customer JWT -> employee API -> 401" (this
     * phase's own required security test, ADR-006 Decision 2): a token
     * signed with a DIFFERENT key — exactly what a customer-service-issued
     * JWT is, since it's signed with JWT_SECRET, not EMPLOYEE_JWT_SECRET —
     * fails signature verification outright and never even reaches the
     * isEmployeeToken claim check. This is a real cryptographic assertion,
     * not a mock: two real SecretKeys, a real signature, a real failed
     * verification.
     */
    @Test
    void parseClaims_returnsEmpty_forTokenSignedWithADifferentKey_modelingACustomerJwt() {
        SecretKey customerServiceKey = Keys.hmacShaKeyFor(CUSTOMER_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenSignedByCustomerService = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "customer@example.com")
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(customerServiceKey)
                .compact();

        assertThat(jwtService.parseClaims(tokenSignedByCustomerService)).isEmpty();
    }

    @Test
    void parseClaims_returnsEmpty_forExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThat(jwtService.parseClaims(expiredToken)).isEmpty();
    }

    @Test
    void isEmployeeToken_isFalse_whenPrincipalTypeClaimIsAbsent() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithoutPrincipalType = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        Claims claims = jwtService.parseClaims(tokenWithoutPrincipalType).orElseThrow();
        assertThat(JwtService.isEmployeeToken(claims)).isFalse();
    }

    @Test
    void constructor_rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 3600)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMPLOYEE_JWT_SECRET must be at least 32 bytes");
    }
}
