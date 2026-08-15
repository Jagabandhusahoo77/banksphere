package com.banksphere.customer.otp;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Centralizes the one cookie this service ever sets — never put into
 * localStorage, never returned in a JSON response body (see ADR-009's
 * browser-storage-strategy section). {@code HttpOnly} (unreachable from
 * frontend JavaScript, so an XSS bug elsewhere in the app can't exfiltrate
 * it — unlike the access token, which the existing architecture already
 * accepts localStorage's XSS exposure for, documented in
 * docs/security/authentication.md), {@code SameSite=Lax} (sent for
 * same-site requests, which localhost:5173 → localhost:8081 qualifies as
 * — same registrable domain, different port — without needing {@code
 * SameSite=None}, which would additionally require {@code Secure} and
 * therefore HTTPS), path-scoped to {@code /api/v1/auth} so it is never
 * sent to account-service/transaction-service/etc. even though those
 * share the same top-level domain in a real deployment.
 *
 * <p>{@code Secure} is controlled by {@code COOKIE_SECURE}
 * (default {@code false}) — local Docker Compose development is plain
 * HTTP, and a browser silently refuses to ever send a {@code Secure}
 * cookie over HTTP, which would break refresh entirely in local dev if
 * left on. This MUST be {@code true} in any real HTTPS deployment — see
 * ADR-009.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "banksphere_refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public void set(HttpServletResponse response, String plaintextToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, plaintextToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
