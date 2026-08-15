package com.banksphere.transaction.controller;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.entity.TransactionType;
import com.banksphere.transaction.exception.TransactionAccessDeniedException;
import com.banksphere.transaction.exception.TransactionNotFoundException;
import com.banksphere.transaction.security.EmployeeJwtValidator;
import com.banksphere.transaction.security.JwtValidator;
import com.banksphere.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security filters disabled — see AccountControllerTest's javadoc for the same reasoning. */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    private static final String AUTH_HEADER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    // See AccountControllerTest's identical field for why this is needed
    // even with addFilters = false.
    @MockBean
    private JwtValidator jwtValidator;

    // Required purely for context loading, same as customer-service's
    // AuthControllerTest — SecurityConfig now wires an
    // EmployeeJwtAuthenticationFilter bean too (Phase 9B). See
    // TransactionControllerEmployeeAuthTest for the tests that actually
    // exercise employee-token behavior through the REAL filter chain
    // (this class disables filters entirely via addFilters = false, so it
    // can't meaningfully prove that either way).
    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    @Test
    void createTransaction_returns201_whenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        TransactionCreateRequest request = new TransactionCreateRequest(
                accountId, TransactionType.DEPOSIT, new BigDecimal("100.00"), "USD", "top-up");
        TransactionResponse response = new TransactionResponse(id, "TXN-ABC123", accountId, TransactionType.DEPOSIT,
                new BigDecimal("100.00"), "USD", TransactionStatus.COMPLETED, "top-up", Instant.now());

        when(transactionService.createTransaction(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TXN-ABC123"));
    }

    @Test
    void getTransaction_returns404_whenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.getTransaction(eq(id), any())).thenThrow(new TransactionNotFoundException(id));

        mockMvc.perform(get("/api/v1/transactions/{id}", id).header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransaction_returns403_whenAccountNotOwnedByCaller() throws Exception {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(transactionService.getTransaction(eq(id), any())).thenThrow(new TransactionAccessDeniedException(accountId));

        mockMvc.perform(get("/api/v1/transactions/{id}", id).header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionsByAccount_returns200WithPagedBody() throws Exception {
        UUID accountId = UUID.randomUUID();
        PageResponse<TransactionResponse> page = new PageResponse<>(java.util.List.of(), 0, 20, 0, 0, true);
        when(transactionService.getTransactionsByAccount(eq(accountId), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions/account/{accountId}?page=0&size=20", accountId)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void getTransactionsByAccount_returns403_whenAccountNotOwnedByCaller() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(transactionService.getTransactionsByAccount(eq(accountId), any(), any()))
                .thenThrow(new TransactionAccessDeniedException(accountId));

        mockMvc.perform(get("/api/v1/transactions/account/{accountId}", accountId)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTransactionsByAccount_defaultsToNewestFirst_whenNoSortRequested() throws Exception {
        UUID accountId = UUID.randomUUID();
        PageResponse<TransactionResponse> page = new PageResponse<>(java.util.List.of(), 0, 20, 0, 0, true);
        when(transactionService.getTransactionsByAccount(eq(accountId), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions/account/{accountId}", accountId).header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(transactionService).getTransactionsByAccount(eq(accountId), argThat(
                (Pageable pageable) -> {
                    Sort.Order order = pageable.getSort().getOrderFor("createdAt");
                    return order != null && order.isDescending();
                }), any());
    }
}
