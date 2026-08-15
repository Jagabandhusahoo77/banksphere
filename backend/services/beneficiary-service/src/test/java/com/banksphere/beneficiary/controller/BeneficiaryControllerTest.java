package com.banksphere.beneficiary.controller;

import com.banksphere.beneficiary.dto.BeneficiaryResponse;
import com.banksphere.beneficiary.dto.CreateBeneficiaryRequest;
import com.banksphere.beneficiary.dto.UpdateBeneficiaryRequest;
import com.banksphere.beneficiary.entity.BeneficiaryStatus;
import com.banksphere.beneficiary.exception.BeneficiaryAccessDeniedException;
import com.banksphere.beneficiary.exception.BeneficiaryNotFoundException;
import com.banksphere.beneficiary.exception.DuplicateBeneficiaryException;
import com.banksphere.beneficiary.security.EmployeeJwtValidator;
import com.banksphere.beneficiary.security.JwtAccessDeniedHandler;
import com.banksphere.beneficiary.security.JwtAuthenticationEntryPoint;
import com.banksphere.beneficiary.security.JwtValidator;
import com.banksphere.beneficiary.security.SecurityConfig;
import com.banksphere.beneficiary.service.BeneficiaryService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real JwtAuthenticationFilter/SecurityConfig run in this test — only
 * JwtValidator's token parsing is mocked. SecurityConfig is explicitly
 * {@code @Import}ed because a plain {@code @WebMvcTest} slice doesn't
 * auto-detect a custom {@code @EnableWebSecurity} configuration, and
 * without it a plain {@code Authentication} controller parameter resolves
 * to null (an unexplained 500) instead of the intended 401/403 — see
 * account-service's AccountControllerTest for the full diagnosis (Phase 3A).
 */
@WebMvcTest(BeneficiaryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class BeneficiaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BeneficiaryService beneficiaryService;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private void authenticateAs(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtValidator.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    private void authenticateAsEmployee(String token, List<String> permissions) {
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("employeeNumber", "EMP000010")
                .add("roles", List.of("KYC_OFFICER"))
                .add("permissions", permissions)
                .build();
        when(employeeJwtValidator.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    private BeneficiaryResponse sampleResponse(UUID id, UUID customerId) {
        return new BeneficiaryResponse(id, customerId, "John Doe", "123456789012", "BANK0001234",
                "Example Bank", "John", BeneficiaryStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private CreateBeneficiaryRequest validCreateRequest() {
        return new CreateBeneficiaryRequest("John Doe", "123456789012", "BANK0001234", "Example Bank", "John");
    }

    @Test
    void createBeneficiary_returns201_whenRequestIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.createBeneficiary(any(), eq(customerId))).thenReturn(sampleResponse(id, customerId));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()));
    }

    @Test
    void createBeneficiary_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/beneficiaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBeneficiary_returns400_whenBeneficiaryNameIsBlank() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(customerId);

        CreateBeneficiaryRequest request = new CreateBeneficiaryRequest("", "123456789012", "BANK0001234", "Example Bank", null);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBeneficiary_returns400_whenIfscIsInvalid() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(customerId);

        CreateBeneficiaryRequest request = new CreateBeneficiaryRequest("John Doe", "123456789012", "not-an-ifsc", "Example Bank", null);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBeneficiary_returns400_whenAccountNumberIsInvalid() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(customerId);

        CreateBeneficiaryRequest request = new CreateBeneficiaryRequest("John Doe", "abc123", "BANK0001234", "Example Bank", null);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBeneficiary_returns409_whenDuplicate() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(beneficiaryService.createBeneficiary(any(), eq(customerId)))
                .thenThrow(new DuplicateBeneficiaryException("123456789012", "BANK0001234"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/beneficiaries")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void getBeneficiaries_returns200WithOnlyCallersBeneficiaries() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(beneficiaryService.getBeneficiaries(customerId)).thenReturn(List.of(sampleResponse(UUID.randomUUID(), customerId)));
        authenticateAs(customerId);

        mockMvc.perform(get("/api/v1/beneficiaries").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()));
    }

    @Test
    void getBeneficiary_returns200_whenFoundAndOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.getBeneficiary(id, customerId)).thenReturn(sampleResponse(id, customerId));
        authenticateAs(customerId);

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getBeneficiary_returns404_whenMissing() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.getBeneficiary(id, customerId)).thenThrow(new BeneficiaryNotFoundException(id));
        authenticateAs(customerId);

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBeneficiary_returns403_whenNotOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.getBeneficiary(id, customerId)).thenThrow(new BeneficiaryAccessDeniedException(id));
        authenticateAs(customerId);

        mockMvc.perform(get("/api/v1/beneficiaries/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBeneficiary_returns200_whenOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.updateBeneficiary(eq(id), any(), eq(customerId))).thenReturn(sampleResponse(id, customerId));
        authenticateAs(customerId);

        UpdateBeneficiaryRequest request = new UpdateBeneficiaryRequest("Johnny Doe", "New Bank", "Johnny");

        mockMvc.perform(put("/api/v1/beneficiaries/{id}", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateBeneficiary_returns403_whenNotOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(beneficiaryService.updateBeneficiary(eq(id), any(), eq(customerId)))
                .thenThrow(new BeneficiaryAccessDeniedException(id));
        authenticateAs(customerId);

        UpdateBeneficiaryRequest request = new UpdateBeneficiaryRequest("Johnny Doe", "New Bank", "Johnny");

        mockMvc.perform(put("/api/v1/beneficiaries/{id}", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivateBeneficiary_returns204_whenOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivateBeneficiary_returns403_whenNotOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new BeneficiaryAccessDeniedException(id))
                .when(beneficiaryService).deactivateBeneficiary(id, customerId);
        authenticateAs(customerId);

        mockMvc.perform(delete("/api/v1/beneficiaries/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    // ---- Phase 9C: employee-facing Customer 360 endpoint ----------------

    @Test
    void getBeneficiariesForEmployee_returns200_whenCallerHasCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(beneficiaryService.getBeneficiariesForEmployee(customerId))
                .thenReturn(List.of(sampleResponse(UUID.randomUUID(), customerId)));
        authenticateAsEmployee("kyc-officer-token", List.of("CUSTOMER_VIEW"));

        mockMvc.perform(get("/api/v1/beneficiaries/employee/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(customerId.toString()));
    }

    @Test
    void getBeneficiariesForEmployee_returns403_whenCallerLacksCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAsEmployee("teller-token", List.of("ACCOUNT_VIEW"));

        mockMvc.perform(get("/api/v1/beneficiaries/employee/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer teller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBeneficiariesForEmployee_returns403_whenCallerIsACustomerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/beneficiaries/employee/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBeneficiariesForEmployee_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/beneficiaries/employee/customer/{customerId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
