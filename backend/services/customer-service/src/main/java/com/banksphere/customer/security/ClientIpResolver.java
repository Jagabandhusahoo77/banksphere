package com.banksphere.customer.security;

import jakarta.servlet.http.HttpServletRequest;

/** Best-effort client IP for rate limiting — see OtpRateLimiter's own javadoc for the demo-scope caveat this feeds into (no reverse-proxy trust chain configured in local dev). */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
