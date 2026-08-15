package com.banksphere.customer.controller;

import com.banksphere.customer.otp.OtpService;
import com.banksphere.customer.otp.dto.OtpVerifyRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmResponse;
import com.banksphere.customer.otp.dto.StepUpRequestRequest;
import com.banksphere.customer.otp.dto.StepUpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpVerifyResponse;
import com.banksphere.customer.security.ClientIpResolver;
import com.banksphere.customer.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PUBLIC: none — every endpoint requires an authenticated customer (see
 * SecurityConfig). {@code customerId} always comes from {@link
 * CurrentUser#id}, never a request body field. {@code /confirm} is
 * reachable by the same customer JWT — it is not employee/service-secret
 * gated, because the caller (account-service) forwards the *customer's
 * own* verified token rather than asserting identity some other way; see
 * StepUpConfirmRequest's javadoc and ADR-009.
 */
@RestController
@RequestMapping("/api/v1/auth/step-up")
@RequiredArgsConstructor
public class StepUpController {

    private final OtpService otpService;

    @PostMapping("/request")
    public ResponseEntity<StepUpRequestResponse> request(
            @Valid @RequestBody StepUpRequestRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(otpService.requestStepUp(
                CurrentUser.id(authentication), request, ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/verify")
    public ResponseEntity<StepUpVerifyResponse> verify(
            @Valid @RequestBody OtpVerifyRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(otpService.verifyStepUp(
                CurrentUser.id(authentication), request.challengeId(), request.otp(), ClientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/confirm")
    public ResponseEntity<StepUpConfirmResponse> confirm(
            @Valid @RequestBody StepUpConfirmRequest request, Authentication authentication) {
        return ResponseEntity.ok(otpService.confirmStepUpExecution(CurrentUser.id(authentication), request));
    }
}
