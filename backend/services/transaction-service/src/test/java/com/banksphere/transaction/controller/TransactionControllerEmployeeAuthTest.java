package com.banksphere.transaction.controller;

import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.entity.TransactionType;
import com.banksphere.transaction.security.EmployeeJwtValidator;
import com.banksphere.transaction.security.JwtAccessDeniedHandler;
import com.banksphere.transaction.security.JwtAuthenticationEntryPoint;
import com.banksphere.transaction.security.JwtValidator;
import com.banksphere.transaction.security.SecurityConfig;
import com.banksphere.transaction.service.TransactionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unlike {@link TransactionControllerTest} (which disables the whole
 * filter chain via {@code addFilters = false} and so cannot meaningfully
 * prove anything about token handling), this class runs the REAL security
 * filter chain — the only way to actually prove the Phase 9B claim that
 * {@code POST /api/v1/transactions} now accepts an employee-signed token
 * as "authenticated," exactly as it already accepted a customer-signed
 * one, with zero change to what the endpoint itself checks (see ADR-001:
 * it never verified identity beyond "a valid JWT" in the first place).
 */
@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class TransactionControllerEmployeeAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private TransactionCreateRequest validRequest() {
        return new TransactionCreateRequest(UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("10000.00"), "INR",
                "CASH DEPOSIT - Branch HQ001");
    }

    @Test
    void createTransaction_returns201_whenCallerPresentsAValidEmployeeToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.createTransaction(any())).thenReturn(new TransactionResponse(
                id, "TXN-ABC123", UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("10000.00"),
                "INR", TransactionStatus.COMPLETED, "CASH DEPOSIT - Branch HQ001", Instant.now()));

        Claims employeeClaims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("employeeNumber", "EMP000010")
                .build();
        when(employeeJwtValidator.parseClaims("employee-token")).thenReturn(Optional.of(employeeClaims));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer employee-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    void createTransaction_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTransaction_returns401_whenTokenIsSignedWithNeitherKnownKey() throws Exception {
        when(jwtValidator.parseClaims("garbage")).thenReturn(Optional.empty());
        when(employeeJwtValidator.parseClaims("garbage")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }
}
