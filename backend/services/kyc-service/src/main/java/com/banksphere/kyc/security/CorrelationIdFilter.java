package com.banksphere.kyc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attaches a correlation id to every request — from the caller's own
 * {@code X-Correlation-Id} header if present, otherwise a freshly
 * generated one — and puts it in the logging {@link MDC} for the lifetime
 * of the request, so every log line (including {@code KycAuditLog}'s
 * structured audit lines) can be correlated back to the same request.
 * Mirrors employee-service's {@code CorrelationIdFilter} exactly (see
 * ADR-006, Decision 7) — the minimum groundwork for a future Audit
 * Service to consume real request-correlated events. Runs before both
 * JWT filters so even a rejected/unauthenticated request's audit line
 * carries a correlation id.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
