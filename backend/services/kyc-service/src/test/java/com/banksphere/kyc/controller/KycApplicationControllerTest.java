package com.banksphere.kyc.controller;

import com.banksphere.kyc.dto.CreateKycApplicationRequest;
import com.banksphere.kyc.dto.KycApplicationResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.entity.DocumentStatus;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.exception.ActiveApplicationExistsException;
import com.banksphere.kyc.exception.DuplicateDocumentException;
import com.banksphere.kyc.exception.InvalidDocumentException;
import com.banksphere.kyc.exception.KycAccessDeniedException;
import com.banksphere.kyc.exception.KycApplicationNotFoundException;
import com.banksphere.kyc.exception.MissingDocumentsException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real JwtAuthenticationFilter/EmployeeJwtAuthenticationFilter/
 * SecurityConfig chain runs in this test — only token parsing is mocked.
 * See account-service's AccountControllerTest for why {@code addFilters
 * = false} is never used here (it would silently null out {@code
 * Authentication}, turning an intended 401/403 into an unexplained 500).
 */
@WebMvcTest(KycApplicationController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class KycApplicationControllerTest {

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

    private void authenticateAsCustomer(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtValidator.parseClaims("customer-token")).thenReturn(Optional.of(claims));
    }

    private void authenticateAsEmployee() {
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of("KYC_OFFICER"))
                .add("permissions", List.of("KYC_VIEW", "KYC_REVIEW", "KYC_APPROVE", "KYC_REJECT"))
                .add("employeeNumber", "EMP000010")
                .add("branchId", UUID.randomUUID().toString())
                .add("branchIfsc", "BANK0HQ0001")
                .build();
        when(employeeJwtValidator.parseClaims("employee-token")).thenReturn(Optional.of(claims));
    }

    private KycApplicationResponse sampleResponse(UUID id, KycStatus status) {
        return new KycApplicationResponse(id, status, "ABCDE1234F", "Engineer", "5-10L",
                null, null, null, List.of(), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void createApplication_returns201_whenPayloadIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.createApplication(eq(customerId), any()))
                .thenReturn(sampleResponse(applicationId, KycStatus.DRAFT));
        authenticateAsCustomer(customerId);

        mockMvc.perform(post("/api/v1/kyc/applications")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateKycApplicationRequest("ABCDE1234F", "Engineer", "5-10L"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createApplication_returns400_whenPanNumberIsMalformed() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAsCustomer(customerId);

        mockMvc.perform(post("/api/v1/kyc/applications")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateKycApplicationRequest("not-a-pan", "Engineer", "5-10L"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createApplication_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/kyc/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateKycApplicationRequest("ABCDE1234F", "Engineer", "5-10L"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createApplication_returns409_whenActiveApplicationAlreadyExists() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(kycApplicationService.createApplication(eq(customerId), any()))
                .thenThrow(new ActiveApplicationExistsException("already in progress"));
        authenticateAsCustomer(customerId);

        mockMvc.perform(post("/api/v1/kyc/applications")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateKycApplicationRequest("ABCDE1234F", "Engineer", "5-10L"))))
                .andExpect(status().isConflict());
    }

    @Test
    void createApplication_returns403_whenCallerIsAnEmployeeToken() throws Exception {
        authenticateAsEmployee();

        mockMvc.perform(post("/api/v1/kyc/applications")
                        .header("Authorization", "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateKycApplicationRequest("ABCDE1234F", "Engineer", "5-10L"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getApplication_returns403_whenApplicationBelongsToAnotherCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.getApplication(eq(customerId), eq(applicationId)))
                .thenThrow(new KycAccessDeniedException("not yours"));
        authenticateAsCustomer(customerId);

        mockMvc.perform(get("/api/v1/kyc/applications/{id}", applicationId)
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getApplication_returns404_whenApplicationDoesNotExist() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.getApplication(eq(customerId), eq(applicationId)))
                .thenThrow(new KycApplicationNotFoundException(applicationId));
        authenticateAsCustomer(customerId);

        mockMvc.perform(get("/api/v1/kyc/applications/{id}", applicationId)
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadDocument_returns201_whenFileIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        KycDocumentResponse response = new KycDocumentResponse(documentId, DocumentType.PAN, DocumentStatus.PENDING,
                "pan.pdf", "application/pdf", 100L, Instant.now(), null, null);
        when(kycApplicationService.uploadDocument(eq(customerId), eq(applicationId), eq(DocumentType.PAN), any()))
                .thenReturn(response);
        authenticateAsCustomer(customerId);

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/kyc/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "PAN")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("PAN"));
    }

    @Test
    void uploadDocument_returns400_whenFileTypeUnsupported() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.uploadDocument(eq(customerId), eq(applicationId), eq(DocumentType.PAN), any()))
                .thenThrow(new InvalidDocumentException("Unsupported file type"));
        authenticateAsCustomer(customerId);

        MockMultipartFile file = new MockMultipartFile("file", "bad.exe", "application/x-msdownload", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/kyc/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "PAN")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadDocument_returns409_whenDuplicateDocumentType() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.uploadDocument(eq(customerId), eq(applicationId), eq(DocumentType.PAN), any()))
                .thenThrow(new DuplicateDocumentException("already submitted"));
        authenticateAsCustomer(customerId);

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/kyc/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", "PAN")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isConflict());
    }

    @Test
    void submit_returns422_whenRequiredDocumentsAreMissing() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.submit(customerId, applicationId))
                .thenThrow(new MissingDocumentsException(List.of(DocumentType.BANK_STATEMENT)));
        authenticateAsCustomer(customerId);

        mockMvc.perform(post("/api/v1/kyc/applications/{id}/submit", applicationId)
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void submit_returns200_whenApplicationIsComplete() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(kycApplicationService.submit(customerId, applicationId))
                .thenReturn(sampleResponse(applicationId, KycStatus.SUBMITTED));
        authenticateAsCustomer(customerId);

        mockMvc.perform(post("/api/v1/kyc/applications/{id}/submit", applicationId)
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
}
