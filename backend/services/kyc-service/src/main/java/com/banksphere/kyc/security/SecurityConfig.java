package com.banksphere.kyc.security;

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
 *
 * <p>{@code /api/v1/kyc/applications/**} (customer-facing): requires
 * authentication only — customer ownership of the specific application is
 * enforced in the service layer via {@link CurrentUser#id}, which also
 * rejects (403) an employee-authenticated request outright, since no
 * {@code @PreAuthorize} on these endpoints would otherwise catch that
 * (they need no special permission, just "a customer").
 *
 * <p>{@code /api/v1/kyc/employee/**} (employee-facing): requires at least
 * {@code KYC_VIEW} at the gateway level as a defense-in-depth floor, with
 * the specific stronger action endpoints (start-review, verify/reject
 * document, request-information under {@code KYC_REVIEW}; approve under
 * {@code KYC_APPROVE}; reject under {@code KYC_REJECT}) each carrying
 * their own {@code @PreAuthorize} — see the controllers. A customer token
 * never carries any {@code KYC_*} authority, so it is rejected with 403
 * before ever reaching {@link EmployeeCurrentUser#identity}.
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
                        .requestMatchers("/api/v1/kyc/employee/**").hasAuthority("KYC_VIEW")
                        .anyRequest().authenticated())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(employeeJwtAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
