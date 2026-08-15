package com.banksphere.employee.security;

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

/**
 * Same shape as every other service's {@code JwtAuthenticationFilter}
 * (never itself rejects a request — an absent/invalid/wrong-key/non-employee
 * token just leaves {@link SecurityContextHolder} empty, and
 * {@link SecurityConfig}'s {@code authorizeHttpRequests}/method-security
 * rules decide whether that's acceptable) with one addition: a token that
 * verifies (right key) but isn't marked {@link JwtService#isEmployeeToken}
 * is treated exactly like an absent token, never authenticated. In
 * practice a non-employee token can't even reach that check, since it was
 * signed with a different key and fails {@link JwtService#parseClaims}
 * outright — this is the defense-in-depth layer, not the primary one.
 *
 * <p>Authorities granted: both {@code ROLE_<ROLE_NAME>} (for any future
 * {@code hasRole(...)} checks) and the raw permission names themselves
 * (e.g. {@code EMPLOYEE_MANAGE}), which is what {@code @PreAuthorize
 * ("hasAuthority(...)")} checks against in the controller layer — RBAC is
 * enforced against permissions, not role names, per ADR-006.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtService::parseClaims)
                .filter(JwtService::isEmployeeToken)
                .ifPresent(claims -> authenticate(claims, request));
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String employeeId = claims.getSubject();
        List<GrantedAuthority> authorities = extractAuthorities(claims);
        var authentication = new UsernamePasswordAuthenticationToken(employeeId, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private List<GrantedAuthority> extractAuthorities(Claims claims) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        JwtService.roles(claims).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        JwtService.permissions(claims).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    }
}
