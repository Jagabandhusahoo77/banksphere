package com.banksphere.beneficiary.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs alongside (never in place of) the existing customer-token {@link
 * JwtAuthenticationFilter} — same explicit-ordering pattern as
 * account-service's identically-named filter; see SecurityConfig. First
 * introduced in Phase 9C for the Customer 360 beneficiaries section
 * ({@code CUSTOMER_VIEW} — see ADR-008).
 */
@Component
@RequiredArgsConstructor
public class EmployeeJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_PRINCIPAL_TYPE = "principalType";
    private static final String PRINCIPAL_TYPE_EMPLOYEE = "EMPLOYEE";
    private static final String CLAIM_EMPLOYEE_NUMBER = "employeeNumber";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";

    private final EmployeeJwtValidator employeeJwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(employeeJwtValidator::parseClaims)
                .filter(EmployeeJwtAuthenticationFilter::isEmployeeToken)
                .ifPresent(claims -> authenticate(claims, request));
        filterChain.doFilter(request, response);
    }

    private static boolean isEmployeeToken(Claims claims) {
        return PRINCIPAL_TYPE_EMPLOYEE.equals(claims.get(CLAIM_PRINCIPAL_TYPE, String.class));
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        EmployeePrincipal principal = new EmployeePrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMPLOYEE_NUMBER, String.class),
                stringList(claims, CLAIM_ROLES),
                stringList(claims, CLAIM_PERMISSIONS));

        List<GrantedAuthority> authorities = new ArrayList<>();
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Claims claims, String key) {
        Object value = claims.get(key);
        return value instanceof List<?> list ? (List<String>) list.stream().map(Object::toString).toList() : List.of();
    }
}
