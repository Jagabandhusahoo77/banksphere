package com.banksphere.customer.controller;

import com.banksphere.customer.dto.AuthResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.LoginRequest;
import com.banksphere.customer.dto.RegisterRequest;
import com.banksphere.customer.exception.RefreshTokenInvalidException;
import com.banksphere.customer.otp.IssuedRefreshToken;
import com.banksphere.customer.otp.OtpService;
import com.banksphere.customer.otp.OtpVerificationResult;
import com.banksphere.customer.otp.RefreshTokenCookies;
import com.banksphere.customer.otp.RefreshTokenProperties;
import com.banksphere.customer.otp.RefreshTokenService;
import com.banksphere.customer.otp.dto.OtpRequestRequest;
import com.banksphere.customer.otp.dto.OtpRequestResponse;
import com.banksphere.customer.otp.dto.OtpVerifyRequest;
import com.banksphere.customer.security.ClientIpResolver;
import com.banksphere.customer.security.CurrentUser;
import com.banksphere.customer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PUBLIC: {@code /register}, {@code /login}, {@code /otp/request}, {@code
 * /otp/verify}, {@code /token/refresh} — see SecurityConfig. {@code
 * /logout}/{@code /me} require an access token.
 *
 * <p>Phase 9D — both {@code /login} and {@code /otp/verify} now also
 * issue a refresh token, delivered exclusively as an {@code HttpOnly}
 * cookie (see {@link RefreshTokenCookies}), never in the JSON body. This
 * is orchestration (translating two services' results into one HTTP
 * response: JSON body + Set-Cookie header), not business logic — the
 * actual token issuance/rotation/revocation logic lives in {@link
 * RefreshTokenService}, and OTP verification logic lives in {@link
 * OtpService}. See ADR-009.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final RefreshTokenProperties refreshTokenProperties;

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest request) {
        CustomerResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);
        issueRefreshCookie(response.customer().id(), httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequestRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(otpService.requestLoginOtp(request.identifier(), ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        OtpVerificationResult result = otpService.verifyLoginOtp(request.challengeId(), request.otp(), ClientIpResolver.resolve(httpRequest));
        AuthResponse response = authService.issueAccessToken(result.customerId());
        issueRefreshCookie(result.customerId(), httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<AuthResponse> refreshToken(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String presented = refreshTokenCookies.read(httpRequest).orElseThrow(RefreshTokenInvalidException::new);
        IssuedRefreshToken rotated = refreshTokenService.rotate(presented);
        AuthResponse response = authService.issueAccessToken(rotated.customerId());
        refreshTokenCookies.set(httpResponse, rotated.plaintext(), refreshTokenProperties.expirySeconds());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(CurrentUser.id(authentication));
        refreshTokenCookies.read(httpRequest).ifPresent(refreshTokenService::revoke);
        refreshTokenCookies.clear(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentCustomer(CurrentUser.id(authentication)));
    }

    private void issueRefreshCookie(java.util.UUID customerId, HttpServletResponse httpResponse) {
        IssuedRefreshToken issued = refreshTokenService.issue(customerId);
        refreshTokenCookies.set(httpResponse, issued.plaintext(), refreshTokenProperties.expirySeconds());
    }
}
