package com.banksphere.employee.controller;

import com.banksphere.employee.dto.AccountSummary;
import com.banksphere.employee.dto.CashDepositRequest;
import com.banksphere.employee.dto.CashDepositResponse;
import com.banksphere.employee.dto.CustomerSearchResponse;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.exception.DownstreamOperationException;
import com.banksphere.employee.security.JwtAccessDeniedHandler;
import com.banksphere.employee.security.JwtAuthenticationEntryPoint;
import com.banksphere.employee.security.JwtService;
import com.banksphere.employee.security.RolePermissions;
import com.banksphere.employee.security.SecurityConfig;
import com.banksphere.employee.service.OperationsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Same RBAC-pinned-against-the-real-mapping pattern as
 * EmployeeControllerTest — {@code CASH_DEPOSIT} is held only by {@code
 * TELLER} and {@code BRANCH_MANAGER} in the existing Phase 9A mapping
 * (confirmed by inspection, not assumed — see RolePermissions), so those
 * are the only two roles this suite expects to succeed; every other role,
 * including {@code ADMIN} (deliberately excluded from money-moving
 * permissions since ADR-006), must be rejected.
 */
@WebMvcTest(OperationsController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class OperationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OperationsService operationsService;

    @MockBean
    private JwtService jwtService;

    private void authenticateAs(String token, Role role) {
        List<String> permissions = RolePermissions.permissionsFor(role).stream().map(Enum::name).toList();
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of(role.name()))
                .add("permissions", permissions)
                .add("employeeNumber", "EMP000010")
                .add("branchId", UUID.randomUUID().toString())
                .add("branchIfsc", "BANK0000001")
                .build();
        when(jwtService.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    private CashDepositRequest validRequest() {
        return new CashDepositRequest(UUID.randomUUID(), new BigDecimal("10000.00"), null);
    }

    @Test
    void cashDeposit_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} is allowed to submit a cash deposit")
    @EnumSource(value = Role.class, names = {"TELLER", "BRANCH_MANAGER"})
    void cashDeposit_returns201_forRolesHoldingCashDeposit(Role role) throws Exception {
        when(operationsService.cashDeposit(any(), any(), any())).thenReturn(new CashDepositResponse(
                "CD-0000000001", UUID.randomUUID(), "617242043877", new BigDecimal("30000.00"), "INR",
                "TXN-ABC123", "COMPLETED", "EMP000010", "HQ001"));
        authenticateAs("role-token", role);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer role-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0} lacks CASH_DEPOSIT and is rejected")
    @EnumSource(value = Role.class, names = {"TELLER", "BRANCH_MANAGER"}, mode = EnumSource.Mode.EXCLUDE)
    void cashDeposit_returns403_forRolesWithoutCashDeposit(Role role) throws Exception {
        authenticateAs("role-token", role);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer role-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashDeposit_returns400_whenAmountIsZero() throws Exception {
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashDepositRequest(UUID.randomUUID(), BigDecimal.ZERO, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cashDeposit_returns403_whenBranchScopeViolated() throws Exception {
        when(operationsService.cashDeposit(any(), any(), any()))
                .thenThrow(new DownstreamOperationException(403, "Outside the caller's own branch"));
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashDeposit_returns404_whenAccountDoesNotExist() throws Exception {
        when(operationsService.cashDeposit(any(), any(), any()))
                .thenThrow(new DownstreamOperationException(404, "Account not found"));
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void cashDeposit_returns422_whenAccountIsNotActive() throws Exception {
        when(operationsService.cashDeposit(any(), any(), any()))
                .thenThrow(new DownstreamOperationException(422, "Account is not ACTIVE"));
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cashDeposit_returns409_onOptimisticLockConflict() throws Exception {
        when(operationsService.cashDeposit(any(), any(), any()))
                .thenThrow(new DownstreamOperationException(409, "The account was concurrently modified, please retry"));
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(post("/api/v1/operations/cash-deposits")
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void customerSearch_returns200_forTellerByAccountNumber() throws Exception {
        when(operationsService.customerSearch("617242043877", null, "valid-token")).thenReturn(
                new CustomerSearchResponse(UUID.randomUUID(), "John Smith",
                        List.of(new AccountSummary(UUID.randomUUID(), "617242043877", "SAVINGS", new BigDecimal("20000.00"), "INR", "ACTIVE"))));
        authenticateAs("valid-token", Role.TELLER);

        mockMvc.perform(get("/api/v1/operations/customer-search")
                        .header("Authorization", "Bearer valid-token")
                        .param("accountNumber", "617242043877"))
                .andExpect(status().isOk());
    }

    /**
     * Every one of the 7 roles happens to hold both {@code CUSTOMER_VIEW}
     * and {@code ACCOUNT_VIEW} in the existing Phase 9A mapping (verified
     * by inspection, not assumed) — so no employee role is actually
     * excluded from this read-only lookup. The meaningful negative case
     * is a token this service can't recognize as an employee token at
     * all: no token, and a malformed one.
     */
    @Test
    void customerSearch_returns401_whenTokenIsMalformed() throws Exception {
        when(jwtService.parseClaims("garbage")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/operations/customer-search")
                        .header("Authorization", "Bearer garbage")
                        .param("accountNumber", "617242043877"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerSearch_returns400_whenNeitherIdentifierProvided() throws Exception {
        when(operationsService.customerSearch(null, null, "teller-token"))
                .thenThrow(new IllegalArgumentException("Provide exactly one of accountNumber or customerId"));
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(get("/api/v1/operations/customer-search").header("Authorization", "Bearer teller-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cashDepositHistory_returns200_forTeller() throws Exception {
        when(operationsService.cashDepositHistory(any(), any())).thenReturn(List.of());
        authenticateAs("teller-token", Role.TELLER);

        mockMvc.perform(get("/api/v1/operations/cash-deposits/history").header("Authorization", "Bearer teller-token"))
                .andExpect(status().isOk());
    }
}
