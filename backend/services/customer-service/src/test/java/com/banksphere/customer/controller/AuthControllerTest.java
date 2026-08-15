package com.banksphere.customer.controller;

import com.banksphere.customer.dto.AuthResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerSummary;
import com.banksphere.customer.dto.LoginRequest;
import com.banksphere.customer.dto.RegisterRequest;
import com.banksphere.customer.entity.CustomerStatus;
import com.banksphere.customer.exception.InvalidCredentialsException;
import com.banksphere.customer.otp.IssuedRefreshToken;
import com.banksphere.customer.otp.OtpService;
import com.banksphere.customer.otp.OtpVerificationResult;
import com.banksphere.customer.otp.RefreshTokenCookies;
import com.banksphere.customer.otp.RefreshTokenProperties;
import com.banksphere.customer.otp.RefreshTokenService;
import com.banksphere.customer.otp.OtpPurpose;
import com.banksphere.customer.otp.dto.OtpRequestRequest;
import com.banksphere.customer.otp.dto.OtpRequestResponse;
import com.banksphere.customer.otp.dto.OtpVerifyRequest;
import com.banksphere.customer.security.EmployeeJwtValidator;
import com.banksphere.customer.security.JwtAccessDeniedHandler;
import com.banksphere.customer.security.JwtAuthenticationEntryPoint;
import com.banksphere.customer.security.JwtService;
import com.banksphere.customer.security.SecurityConfig;
import com.banksphere.customer.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** See CustomerControllerTest's javadoc for why SecurityConfig must be explicitly @Import-ed here. */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private RefreshTokenCookies refreshTokenCookies;

    @MockBean
    private RefreshTokenProperties refreshTokenProperties;

    /**
     * Not exercised directly by any test in this class — required purely
     * because SecurityConfig now wires an EmployeeJwtAuthenticationFilter
     * bean (Phase 9B) alongside the customer one, so the @WebMvcTest slice
     * needs a bean for its EmployeeJwtValidator dependency to even start.
     * See CustomerControllerTest for the tests that actually exercise
     * employee-token behavior.
     */
    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    @BeforeEach
    void stubRefreshTokenIssuance() {
        // Every successful login/otp-verify path issues a refresh cookie —
        // stub a default so those tests don't NPE on refreshTokenService's
        // return value; not every test in this class touches this path,
        // so lenient() avoids UnnecessaryStubbingException for those that don't.
        lenient().when(refreshTokenService.issue(any()))
                .thenReturn(new IssuedRefreshToken("plaintext-refresh-token", UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(1_209_600)));
        lenient().when(refreshTokenProperties.expirySeconds()).thenReturn(1_209_600L);
    }

    private void authenticateAs(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtService.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    @Test
    void register_returns201WithoutPasswordFields() throws Exception {
        UUID id = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St", "Password123");
        CustomerResponse response = new CustomerResponse(id, "Jane", "Doe", "jane.doe@example.com",
                "+1-555-0100", LocalDate.of(1990, 5, 20), "123 Main St", CustomerStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_returns400_whenPasswordIsTooWeak() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St", "weak"); // too short, no digit

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithAccessToken_whenCredentialsAreValid() throws Exception {
        UUID id = UUID.randomUUID();
        AuthResponse response = AuthResponse.of("signed.jwt.token", 3600,
                new CustomerSummary(id, "Jane", "Doe", "jane.doe@example.com"));

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jane.doe@example.com", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.customer.email").value("jane.doe@example.com"))
                .andExpect(jsonPath("$.customer.password").doesNotExist());
    }

    @Test
    void login_returns401_whenCredentialsAreInvalid() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jane.doe@example.com", "WrongPassword1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void me_returns200WithAuthenticatedCustomersProfile() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerResponse response = new CustomerResponse(id, "Jane", "Doe", "jane.doe@example.com",
                "+1-555-0100", LocalDate.of(1990, 5, 20), "123 Main St", CustomerStatus.ACTIVE,
                Instant.now(), Instant.now());
        when(authService.getCurrentCustomer(eq(id))).thenReturn(response);
        authenticateAs(id);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"));
    }

    @Test
    void me_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        authenticateAs(id);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }

    // ---- Phase 9D: OTP login + refresh token ----------------------------

    @Test
    void requestOtp_returns200_andIsReachableWithoutAuthentication() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(otpService.requestLoginOtp(any(), any())).thenReturn(OtpRequestResponse.of(challengeId));

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequestRequest("jane.doe@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()));
    }

    @Test
    void verifyOtp_returns200WithAccessToken_andSetsRefreshCookie_whenOtpIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(otpService.verifyLoginOtp(eq(challengeId), eq("123456"), any()))
                .thenReturn(new OtpVerificationResult(customerId, OtpPurpose.LOGIN));
        AuthResponse response = AuthResponse.of("signed.jwt.token", 3600,
                new CustomerSummary(customerId, "Jane", "Doe", "jane.doe@example.com"));
        when(authService.issueAccessToken(customerId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(challengeId, "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.customer.email").value("jane.doe@example.com"));

        // The access token comes back in JSON; the refresh token must
        // never appear there — only via the Set-Cookie header, which
        // refreshTokenCookies (mocked here) is responsible for. Asserting
        // it was invoked is the controller-level proof that this endpoint
        // wires cookie issuance in, not just the JSON body.
        verify(refreshTokenCookies).set(any(), eq("plaintext-refresh-token"), eq(1_209_600L));
    }

    @Test
    void verifyOtp_returns400_whenOtpIsInvalidShape() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(UUID.randomUUID(), "abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshToken_returns401_whenNoRefreshCookiePresent() throws Exception {
        when(refreshTokenCookies.read(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isUnauthorized());

        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    void refreshToken_returns200WithNewAccessToken_whenRefreshCookieIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(refreshTokenCookies.read(any())).thenReturn(Optional.of("presented-refresh-token"));
        when(refreshTokenService.rotate("presented-refresh-token"))
                .thenReturn(new IssuedRefreshToken("new-refresh-token", UUID.randomUUID(), customerId, Instant.now().plusSeconds(1_209_600)));
        AuthResponse response = AuthResponse.of("rotated.jwt.token", 3600,
                new CustomerSummary(customerId, "Jane", "Doe", "jane.doe@example.com"));
        when(authService.issueAccessToken(customerId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("rotated.jwt.token"));

        verify(refreshTokenCookies).set(any(), eq("new-refresh-token"), eq(1_209_600L));
    }

    @Test
    void logout_revokesAndClearsTheRefreshCookie() throws Exception {
        UUID id = UUID.randomUUID();
        authenticateAs(id);
        when(refreshTokenCookies.read(any())).thenReturn(Optional.of("presented-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revoke("presented-refresh-token");
        verify(refreshTokenCookies).clear(any());
    }
}
