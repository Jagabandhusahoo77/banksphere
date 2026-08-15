package com.banksphere.customer.controller;

import com.banksphere.customer.otp.OtpPurpose;
import com.banksphere.customer.otp.OtpService;
import com.banksphere.customer.otp.dto.OtpVerifyRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmResponse;
import com.banksphere.customer.otp.dto.StepUpRequestRequest;
import com.banksphere.customer.otp.dto.StepUpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpVerifyResponse;
import com.banksphere.customer.otp.dto.TransferStepUpContext;
import com.banksphere.customer.security.EmployeeJwtValidator;
import com.banksphere.customer.security.JwtAccessDeniedHandler;
import com.banksphere.customer.security.JwtAuthenticationEntryPoint;
import com.banksphere.customer.security.JwtService;
import com.banksphere.customer.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level proof that {@code customerId} for every step-up
 * endpoint comes from the authenticated JWT (via {@code CurrentUser}),
 * never from the request body — see StepUpController's own javadoc. Every
 * test authenticates as one customer and asserts the service was invoked
 * with THAT customer's id, independent of anything in the JSON body.
 */
@WebMvcTest(StepUpController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class StepUpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OtpService otpService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private void authenticateAs(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtService.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    @Test
    void request_returns401_withoutAValidToken() throws Exception {
        StepUpRequestRequest request = new StepUpRequestRequest(OtpPurpose.STEP_UP_TRANSFER,
                new TransferStepUpContext(UUID.randomUUID(), "222222222222", "BANK0000001", new BigDecimal("100000.00"), "INR"));

        mockMvc.perform(post("/api/v1/auth/step-up/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_derivesCustomerIdFromTheJwt_neverFromTheRequestBody() throws Exception {
        UUID authenticatedCustomerId = UUID.randomUUID();
        authenticateAs(authenticatedCustomerId);
        UUID challengeId = UUID.randomUUID();
        when(otpService.requestStepUp(eq(authenticatedCustomerId), any(), any()))
                .thenReturn(new StepUpRequestResponse(challengeId, Instant.now().plusSeconds(300)));

        StepUpRequestRequest request = new StepUpRequestRequest(OtpPurpose.STEP_UP_TRANSFER,
                new TransferStepUpContext(UUID.randomUUID(), "222222222222", "BANK0000001", new BigDecimal("100000.00"), "INR"));

        mockMvc.perform(post("/api/v1/auth/step-up/request")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()));

        verify(otpService).requestStepUp(eq(authenticatedCustomerId), any(), any());
    }

    @Test
    void verify_derivesCustomerIdFromTheJwt() throws Exception {
        UUID authenticatedCustomerId = UUID.randomUUID();
        authenticateAs(authenticatedCustomerId);
        UUID challengeId = UUID.randomUUID();
        when(otpService.verifyStepUp(eq(authenticatedCustomerId), eq(challengeId), eq("123456"), any()))
                .thenReturn(new StepUpVerifyResponse(true, challengeId, Instant.now().plusSeconds(300)));

        mockMvc.perform(post("/api/v1/auth/step-up/verify")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(challengeId, "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void confirm_derivesCustomerIdFromTheJwt_neverFromTheRequestBody() throws Exception {
        UUID authenticatedCustomerId = UUID.randomUUID();
        authenticateAs(authenticatedCustomerId);
        UUID challengeId = UUID.randomUUID();
        TransferStepUpContext context = new TransferStepUpContext(UUID.randomUUID(), "222222222222", "BANK0000001", new BigDecimal("100000.00"), "INR");
        when(otpService.confirmStepUpExecution(eq(authenticatedCustomerId), any()))
                .thenReturn(new StepUpConfirmResponse(true, challengeId));

        mockMvc.perform(post("/api/v1/auth/step-up/confirm")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));

        verify(otpService).confirmStepUpExecution(eq(authenticatedCustomerId), any());
    }
}
