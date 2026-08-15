package com.banksphere.customer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.banksphere.customer.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs when an unauthenticated (missing/invalid/expired token) request
 * hits a protected endpoint — returns the same {@link ErrorResponse} shape
 * every other exception handler in this service uses, with no internal
 * exception detail (never leaks *why* the token was rejected beyond
 * "authentication required").
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Authentication required", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
