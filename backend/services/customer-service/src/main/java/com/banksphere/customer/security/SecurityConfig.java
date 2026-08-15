package com.banksphere.customer.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Public vs authenticated endpoints for customer-service (see
 * docs/security/authorization.md for the full table across all three
 * services):
 *
 * PUBLIC:        POST /api/v1/auth/register, POST /api/v1/auth/login,
 *                POST /api/v1/auth/otp/request, POST /api/v1/auth/otp/verify,
 *                POST /api/v1/auth/token/refresh (Phase 9D — the caller
 *                has no access token at exactly the moment it needs this
 *                endpoint, by definition, so it cannot require one; the
 *                HttpOnly refresh cookie is what actually authorizes it —
 *                see RefreshTokenCookies/RefreshTokenService),
 *                GET /api/v1/auth/dev/otp-inbox (Phase 9D — only exists
 *                in the application context at all when explicitly
 *                enabled; see DevOtpInboxController),
 *                /actuator/health, /actuator/info
 * AUTHENTICATED: everything else, including POST /api/v1/customers
 *                (legacy direct-create — superseded by /auth/register,
 *                kept but not public; see ADR-002), GET/PUT
 *                /api/v1/customers/{id} (ownership enforced in the
 *                controller/service layer, not here — see
 *                CustomerController), and all of /api/v1/auth/step-up/**
 *                (Phase 9D — step-up is additional authentication for an
 *                ALREADY-authenticated customer, never reachable without
 *                a valid access token first — see StepUpController).
 *
 * <p>CSRF remains disabled (stateless API) even though Phase 9D
 * introduces this service's first cookie: the refresh-token cookie is
 * {@code SameSite=Lax} (see RefreshTokenCookies), which browsers refuse
 * to attach to a cross-site POST at all — the actual CSRF defense for
 * {@code /token/refresh}/{@code /logout}, not Spring Security's CSRF
 * token machinery. See ADR-009.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmployeeJwtAuthenticationFilter employeeJwtAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookie-based session to protect
                .cors(Customizer.withDefaults()) // delegates to the existing WebMvc CorsConfig
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/otp/request", "/api/v1/auth/otp/verify").permitAll()
                        .requestMatchers("/api/v1/auth/token/refresh").permitAll()
                        .requestMatchers("/api/v1/auth/dev/otp-inbox").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(employeeJwtAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
