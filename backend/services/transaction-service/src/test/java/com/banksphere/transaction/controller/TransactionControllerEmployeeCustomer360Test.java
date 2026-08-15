package com.banksphere.transaction.controller;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.entity.TransactionType;
import com.banksphere.transaction.security.EmployeeJwtValidator;
import com.banksphere.transaction.security.JwtAccessDeniedHandler;
import com.banksphere.transaction.security.JwtAuthenticationEntryPoint;
import com.banksphere.transaction.security.JwtValidator;
import com.banksphere.transaction.security.SecurityConfig;
import com.banksphere.transaction.service.TransactionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9C — the Customer 360 employee endpoint, proven against the real
 * security filter chain (see TransactionControllerEmployeeAuthTest's
 * javadoc for why {@code addFilters = false} is never used here).
 */
@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class TransactionControllerEmployeeCustomer360Test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

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

    private void authenticateAsCustomer(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtValidator.parseClaims("customer-token")).thenReturn(Optional.of(claims));
    }

    @Test
    void getTransactionsByAccountForEmployee_returns200_whenCallerHasTransactionView() throws Exception {
        UUID accountId = UUID.randomUUID();
        TransactionResponse txn = new TransactionResponse(UUID.randomUUID(), "TXN-ABC123", accountId,
                TransactionType.DEPOSIT, new BigDecimal("500.00"), "INR", TransactionStatus.COMPLETED, "desc", Instant.now());
        when(transactionService.getTransactionsByAccountForEmployee(any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(txn), PageRequest.of(0, 20), 1)));
        authenticateAsEmployee("kyc-officer-token", List.of("TRANSACTION_VIEW"));

        mockMvc.perform(get("/api/v1/transactions/employee/account/{accountId}", accountId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference").value("TXN-ABC123"));
    }

    @Test
    void getTransactionsByAccountForEmployee_returns403_whenCallerLacksTransactionView() throws Exception {
        UUID accountId = UUID.randomUUID();
        authenticateAsEmployee("teller-token", List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"));

        mockMvc.perform(get("/api/v1/transactions/employee/account/{accountId}", accountId)
                        .header("Authorization", "Bearer teller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionsByAccountForEmployee_returns403_whenCallerIsACustomerToken() throws Exception {
        UUID accountId = UUID.randomUUID();
        authenticateAsCustomer(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/transactions/employee/account/{accountId}", accountId)
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionsByAccountForEmployee_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/employee/account/{accountId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
