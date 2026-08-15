package com.banksphere.customer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend is served from a different origin (different port) than this
 * service, so browsers enforce CORS on every call. Without this, the React
 * app cannot reach the API at all even though direct HTTP clients (curl,
 * Postman, backend-to-backend calls) are unaffected — CORS is a
 * browser-only mechanism.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${banksphere.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                // Phase 9D — flipped from false: the refresh-token HttpOnly
                // cookie (see RefreshTokenCookies) only reaches the browser
                // and is only sent back on subsequent requests if the
                // browser is allowed to treat this as a credentialed
                // request. Safe specifically because allowedOrigins above
                // is always an explicit origin list, never a wildcard —
                // browsers refuse allowCredentials(true) with "*" anyway.
                .allowCredentials(true)
                .maxAge(3600);
    }
}
