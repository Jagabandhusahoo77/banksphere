package com.banksphere.account.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * PUBLIC: /actuator/health, /actuator/info.
 * AUTHENTICATED: everything else, i.e. all of /api/v1/accounts/** —
 * account ownership (does the JWT customer actually own the requested
 * account?) is enforced in AccountServiceImpl, not here — Spring Security
 * only proves *who* the caller is, not what they're allowed to touch. See
 * docs/security/authorization.md.
 *
 * <p>{@code @EnableMethodSecurity} is new in Phase 9B, mirroring
 * employee-service's own {@code SecurityConfig} — it's what makes
 * {@code @PreAuthorize("hasAuthority('CASH_DEPOSIT')")} on the new
 * employee-only endpoints (see AccountController) actually enforce
 * anything. It has no effect on any existing customer endpoint, none of
 * which carry a {@code @PreAuthorize} annotation.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmployeeJwtAuthenticationFilter employeeJwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
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
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Explicitly ordered before the customer filter (not just
                // "before UsernamePasswordAuthenticationFilter" a second
                // time) so ordering is deterministic. In practice either
                // order works — a token can only ever verify against one
                // of the two keys — but explicit ordering documents intent.
                .addFilterBefore(employeeJwtAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
