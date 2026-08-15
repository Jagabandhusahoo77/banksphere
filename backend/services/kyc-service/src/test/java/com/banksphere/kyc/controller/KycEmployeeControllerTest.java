package com.banksphere.kyc.controller;

import com.banksphere.kyc.dto.KycApplicationDetailResponse;
import com.banksphere.kyc.dto.KycQueueItemResponse;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.exception.InvalidStateTransitionException;
import com.banksphere.kyc.security.EmployeeJwtValidator;
import com.banksphere.kyc.security.JwtAccessDeniedHandler;
import com.banksphere.kyc.security.JwtAuthenticationEntryPoint;
import com.banksphere.kyc.security.JwtValidator;
import com.banksphere.kyc.security.SecurityConfig;
import com.banksphere.kyc.service.KycApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** See KycApplicationControllerTest's javadoc for why the real security filter chain runs (never {@code addFilters = false}). */
@WebMvcTest(KycEmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class KycEmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KycApplicationService kycApplicationService;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private static final List<String> KYC_OFFICER_PERMISSIONS =
            List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "KYC_VIEW", "KYC_REVIEW", "KYC_APPROVE", "KYC_REJECT");

    private void authenticateAsCustomer(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtValidator.parseClaims("customer-token")).thenReturn(Optional.of(claims));
    }

    private void authenticateAsEmployee(String token, String role, List<String> permissions) {
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of(role))
                .add("permissions", permissions)
                .add("employeeNumber", "EMP000010")
                .add("branchId", UUID.randomUUID().toString())
                .add("branchIfsc", "BANK0HQ0001")
                .build();
        when(employeeJwtValidator.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    private KycApplicationDetailResponse sampleDetail(UUID id, KycStatus status) {
        return new KycApplicationDetailResponse(id, UUID.randomUUID(), status, "ABCDE1234F", "Engineer", "5-10L",
                null, Instant.now(), null, null, null, List.of(), List.of(), List.of(), 0L, Instant.now(), Instant.now());
    }

    @Test
    void getQueue_returns200_forKycOfficer() throws Exception {
        when(kycApplicationService.getQueue(any()))
                .thenReturn(List.of(new KycQueueItemResponse(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), KycStatus.SUBMITTED, 3, 4, null)));
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(get("/api/v1/kyc/employee/queue")
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentsRequired").value(4));
    }

    @Test
    void getQueue_returns403_whenCallerIsACustomerToken() throws Exception {
        authenticateAsCustomer(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/kyc/employee/queue")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getQueue_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/kyc/employee/queue"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TELLER", "LOAN_OFFICER", "CARD_OFFICER"})
    void approve_returns403_forRolesWithoutKycApprove(String role) throws Exception {
        UUID applicationId = UUID.randomUUID();
        authenticateAsEmployee("role-token", role, List.of("CUSTOMER_VIEW"));

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer role-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_returns200_forKycOfficer() throws Exception {
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.approve(any(), eq(applicationId)))
                .thenReturn(sampleDetail(applicationId, KycStatus.APPROVED));
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_returns422_whenApplicationNotUnderReview() throws Exception {
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.approve(any(), eq(applicationId)))
                .thenThrow(new InvalidStateTransitionException(KycStatus.DRAFT, KycStatus.APPROVED));
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reject_returns200_forKycOfficer() throws Exception {
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.reject(any(), eq(applicationId), eq("PAN mismatch")))
                .thenReturn(sampleDetail(applicationId, KycStatus.REJECTED));
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/reject", applicationId)
                        .header("Authorization", "Bearer kyc-officer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.banksphere.kyc.dto.ReasonRequest("PAN mismatch"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reject_returns400_whenReasonIsBlank() throws Exception {
        UUID applicationId = UUID.randomUUID();
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/reject", applicationId)
                        .header("Authorization", "Bearer kyc-officer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.banksphere.kyc.dto.ReasonRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startReview_returns403_forRoleWithoutKycReview() throws Exception {
        UUID applicationId = UUID.randomUUID();
        authenticateAsEmployee("teller-token", "TELLER", List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"));

        mockMvc.perform(post("/api/v1/kyc/employee/applications/{id}/start-review", applicationId)
                        .header("Authorization", "Bearer teller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getApplicationForCustomer_returns200_whenApplicationExists() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(kycApplicationService.getApplicationForCustomer(customerId))
                .thenReturn(java.util.Optional.of(sampleDetail(UUID.randomUUID(), KycStatus.SUBMITTED)));
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(get("/api/v1/kyc/employee/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk());
    }

    @Test
    void getApplicationForCustomer_returns204_whenCustomerNeverStartedKyc() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(kycApplicationService.getApplicationForCustomer(customerId)).thenReturn(java.util.Optional.empty());
        authenticateAsEmployee("kyc-officer-token", "KYC_OFFICER", KYC_OFFICER_PERMISSIONS);

        mockMvc.perform(get("/api/v1/kyc/employee/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getApplication_returns200_forBranchManagerWithKycView() throws Exception {
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.getApplicationDetail(applicationId))
                .thenReturn(sampleDetail(applicationId, KycStatus.UNDER_REVIEW));
        authenticateAsEmployee("bm-token", "BRANCH_MANAGER", List.of("KYC_VIEW", "KYC_REVIEW"));

        mockMvc.perform(get("/api/v1/kyc/employee/applications/{id}", applicationId)
                        .header("Authorization", "Bearer bm-token"))
                .andExpect(status().isOk());
    }
}
