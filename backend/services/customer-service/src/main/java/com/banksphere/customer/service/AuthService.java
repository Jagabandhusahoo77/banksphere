package com.banksphere.customer.service;

import com.banksphere.customer.dto.AuthResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.LoginRequest;
import com.banksphere.customer.dto.RegisterRequest;

import java.util.UUID;

public interface AuthService {

    CustomerResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /**
     * Phase 9D — issues a fresh access token for a customer already
     * proven by some other means (OTP verification, or a rotated refresh
     * token) — the shared tail end of every login/refresh path, never
     * itself re-checks a password. {@code customerId} must already be
     * trusted by the caller (never taken from a request body — see
     * OtpServiceImpl#verifyLoginOtp and RefreshTokenService#rotate,
     * the only two legitimate callers).
     */
    AuthResponse issueAccessToken(UUID customerId);

    /**
     * Stateless-JWT logout for the access token — see
     * docs/security/authentication.md's Logout section for why this
     * remains a no-op for the access token itself (no server-side
     * session or token denylist exists in this phase). The refresh
     * token, if any, IS actually revoked — see AuthController, which
     * calls RefreshTokenService directly (a session-cookie concern, not
     * this access-token-focused service's job).
     */
    void logout(UUID customerId);

    CustomerResponse getCurrentCustomer(UUID customerId);
}
