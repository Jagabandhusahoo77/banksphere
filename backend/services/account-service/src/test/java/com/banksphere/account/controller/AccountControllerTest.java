package com.banksphere.account.controller;

import com.banksphere.account.dto.AccountCreateRequest;
import com.banksphere.account.dto.AccountResponse;
import com.banksphere.account.dto.AmountRequest;
import com.banksphere.account.dto.EmployeeDepositResponse;
import com.banksphere.account.dto.ResolveRecipientRequest;
import com.banksphere.account.dto.ResolveRecipientResponse;
import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.dto.TransferResponse;
import com.banksphere.account.entity.AccountStatus;
import com.banksphere.account.entity.AccountType;
import com.banksphere.account.exception.AccountAccessDeniedException;
import com.banksphere.account.exception.AccountNotActiveException;
import com.banksphere.account.exception.AccountNotFoundException;
import com.banksphere.account.exception.BranchScopeViolationException;
import com.banksphere.account.exception.CurrencyMismatchException;
import com.banksphere.account.exception.InsufficientBalanceException;
import com.banksphere.account.exception.RecipientNotFoundException;
import com.banksphere.account.exception.UnsupportedIfscException;
import com.banksphere.account.security.EmployeeJwtValidator;
import com.banksphere.account.security.JwtAccessDeniedHandler;
import com.banksphere.account.security.JwtAuthenticationEntryPoint;
import com.banksphere.account.security.JwtValidator;
import com.banksphere.account.security.SecurityConfig;
import com.banksphere.account.service.AccountService;
import com.banksphere.account.service.AccountServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real JwtAuthenticationFilter/SecurityConfig run in this test — only
 * JwtValidator's token parsing is mocked. See CustomerControllerTest
 * (customer-service) for why {@code addFilters = false} +
 * SecurityMockMvcRequestPostProcessors.user(...) was abandoned: disabling
 * the filter chain also disables the Spring Security filter that
 * populates {@code HttpServletRequest.getUserPrincipal()}, which is what
 * a plain {@code Authentication} controller parameter resolves from —
 * leaving it null and causing a NullPointerException (an unexplained 500)
 * instead of the intended 401/403.
 *
 * <p>SecurityConfig is explicitly {@code @Import}ed for the same reason
 * documented on customer-service's CustomerControllerTest: a plain
 * {@code @WebMvcTest} slice doesn't auto-detect a custom
 * {@code @EnableWebSecurity} configuration, so without this import Spring
 * Boot falls back to its own CSRF-protected default chain instead.
 */
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @MockBean
    private JwtValidator jwtValidator;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private void authenticateAs(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtValidator.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    /** Phase 9B — same pattern as employee-service's own controller tests, pinned against the real RolePermissions mapping. */
    private void authenticateAsEmployee(String token, String role, List<String> permissions, String branchIfsc) {
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of(role))
                .add("permissions", permissions)
                .add("employeeNumber", "EMP000010")
                .add("branchId", UUID.randomUUID().toString())
                .add("branchIfsc", branchIfsc)
                .build();
        when(employeeJwtValidator.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    private AccountResponse sampleResponse(UUID id, UUID customerId, BigDecimal balance) {
        return new AccountResponse(id, customerId, "123456789012", "BANK0000001", AccountType.SAVINGS,
                balance, "USD", AccountStatus.ACTIVE, Instant.now(), Instant.now());
    }

    @Test
    void createAccount_ignoresClientSuppliedAccountNumberIfscAndCustomerId_usingOnlyServerGeneratedValues() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID generatedId = UUID.randomUUID();
        UUID someoneElsesCustomerId = UUID.randomUUID();
        when(accountService.createAccount(any(), eq(customerId), any()))
                .thenReturn(sampleResponse(generatedId, customerId, BigDecimal.ZERO));
        authenticateAs(customerId);

        // A client attempting to smuggle in an account number, an IFSC, and
        // someone else's customerId — none of these are fields on
        // AccountCreateRequest, so Jackson has nothing to bind them to; this
        // proves that structurally, not just by convention.
        String maliciousBody = """
                {
                  "accountType": "SAVINGS",
                  "currency": "USD",
                  "accountNumber": "000000000001",
                  "ifsc": "HACK0000001",
                  "customerId": "%s"
                }
                """.formatted(someoneElsesCustomerId);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("123456789012"))
                .andExpect(jsonPath("$.ifsc").value("BANK0000001"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()));

        ArgumentCaptor<AccountCreateRequest> captor = ArgumentCaptor.forClass(AccountCreateRequest.class);
        verify(accountService).createAccount(captor.capture(), eq(customerId), any());
        assertThat(captor.getValue().accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(captor.getValue().currency()).isEqualTo("USD");
    }

    @Test
    void deposit_returns200AndUpdatedBalance_whenAmountIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(accountService.deposit(eq(id), any(), eq(customerId), any()))
                .thenReturn(sampleResponse(id, customerId, new BigDecimal("150.00")));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("50.00"), "top-up"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.00));
    }

    @Test
    void deposit_returns401_whenNoTokenSupplied() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("50.00"), "top-up"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deposit_returns400_whenAmountIsZeroOrNegative() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(BigDecimal.ZERO, "bad"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_returns403_whenAccountNotOwnedByCaller() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(accountService.deposit(eq(id), any(), eq(customerId), any()))
                .thenThrow(new AccountAccessDeniedException(id));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/deposit", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("50.00"), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdraw_returns422_whenBalanceIsInsufficient() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(accountService.withdraw(eq(id), any(), eq(customerId), any()))
                .thenThrow(new InsufficientBalanceException(id, new BigDecimal("10.00"), new BigDecimal("50.00")));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("50.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void withdraw_returns404_whenAccountMissing() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(accountService.withdraw(eq(id), any(), eq(customerId), any())).thenThrow(new AccountNotFoundException(id));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10.00"), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdraw_returns403_whenAccountNotOwnedByCaller() throws Exception {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(accountService.withdraw(eq(id), any(), eq(customerId), any()))
                .thenThrow(new AccountAccessDeniedException(id));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/{id}/withdraw", id)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10.00"), null))))
                .andExpect(status().isForbidden());
    }

    // ---- transfer() ----

    private static final String DESTINATION_ACCOUNT_NUMBER = "222222222222";

    private TransferResponse sampleTransferResponse(UUID sourceId, BigDecimal amount) {
        return new TransferResponse(UUID.randomUUID(), sourceId, DESTINATION_ACCOUNT_NUMBER,
                AccountServiceImpl.BANKSPHERE_IFSC, amount, "USD", "COMPLETED", Instant.now());
    }

    private TransferRequest sampleTransferRequest(UUID sourceId, BigDecimal amount, String description) {
        return new TransferRequest(sourceId, DESTINATION_ACCOUNT_NUMBER, AccountServiceImpl.BANKSPHERE_IFSC, amount, description, null, null);
    }

    @Test
    void transfer_returns200AndTransferResponse_whenValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenReturn(sampleTransferResponse(sourceId, new BigDecimal("5000.00")));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("5000.00"), "rent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceAccountId").value(sourceId.toString()))
                .andExpect(jsonPath("$.destinationAccountNumber").value(DESTINATION_ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.destinationIfsc").value(AccountServiceImpl.BANKSPHERE_IFSC))
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                // Never the internal destination id — see ADR-005.
                .andExpect(jsonPath("$.destinationAccountId").doesNotExist());
    }

    @Test
    void transfer_returns401_whenNoTokenSupplied() throws Exception {
        UUID sourceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transfer_returns400_whenAmountIsZeroOrNegative() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, BigDecimal.ZERO, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_returns400_whenDestinationAccountNumberIsNotTwelveDigits() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(sourceId, "12345", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_returns400_whenDestinationIfscIsMalformed() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(sourceId, DESTINATION_ACCOUNT_NUMBER, "not-an-ifsc", new BigDecimal("50.00"), null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_returns400_whenRequestBodyIsMalformed() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_returns400_whenSourceAndDestinationAreTheSameAccount() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new IllegalArgumentException("Source and destination account must be different"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(accountId, new BigDecimal("10.00"), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_returns403_whenSourceNotOwnedByCaller() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new AccountAccessDeniedException(sourceId));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfer_returns404_whenRecipientNotFound() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new RecipientNotFoundException(DESTINATION_ACCOUNT_NUMBER));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_returns404_whenSourceAccountMissing() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new AccountNotFoundException(sourceId));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_returns422_whenBalanceIsInsufficient() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new InsufficientBalanceException(sourceId, new BigDecimal("10.00"), new BigDecimal("50.00")));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_returns422_whenAccountIsNotActive() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new AccountNotActiveException(destinationId, AccountStatus.CLOSED));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_returns422_whenCurrencyMismatch() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new CurrencyMismatchException(sourceId, "USD", destinationId, "EUR"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_returns422_whenDestinationIfscIsNotBankSphere() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(accountService.transfer(any(), eq(customerId), any()))
                .thenThrow(new UnsupportedIfscException("HDFC0001234"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleTransferRequest(sourceId, new BigDecimal("50.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- resolveRecipient() ----

    @Test
    void resolveRecipient_returns200WithMinimalDetails_whenRecipientIsValid() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(accountService.resolveRecipient(any()))
                .thenReturn(new ResolveRecipientResponse(DESTINATION_ACCOUNT_NUMBER, AccountServiceImpl.BANKSPHERE_IFSC, "BankSphere"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest(DESTINATION_ACCOUNT_NUMBER, AccountServiceImpl.BANKSPHERE_IFSC))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(DESTINATION_ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.ifsc").value(AccountServiceImpl.BANKSPHERE_IFSC))
                .andExpect(jsonPath("$.bankName").value("BankSphere"))
                // Minimal response — no internal id, no customerId, no balance
                // (see ADR-005's enumeration-safety reasoning).
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist());
    }

    @Test
    void resolveRecipient_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest(DESTINATION_ACCOUNT_NUMBER, AccountServiceImpl.BANKSPHERE_IFSC))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolveRecipient_returns400_whenAccountNumberIsNotTwelveDigits() throws Exception {
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest("abc123", AccountServiceImpl.BANKSPHERE_IFSC))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveRecipient_returns404_whenRecipientNotFound() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(accountService.resolveRecipient(any())).thenThrow(new RecipientNotFoundException("999999999999"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest("999999999999", AccountServiceImpl.BANKSPHERE_IFSC))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveRecipient_returns422_whenIfscIsNotBankSphere() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(accountService.resolveRecipient(any())).thenThrow(new UnsupportedIfscException("HDFC0001234"));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest(DESTINATION_ACCOUNT_NUMBER, "HDFC0001234"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resolveRecipient_returns422_whenRecipientAccountIsNotActive() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(accountService.resolveRecipient(any())).thenThrow(new AccountNotActiveException(destinationId, AccountStatus.CLOSED));
        authenticateAs(customerId);

        mockMvc.perform(post("/api/v1/accounts/resolve-recipient")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResolveRecipientRequest(DESTINATION_ACCOUNT_NUMBER, AccountServiceImpl.BANKSPHERE_IFSC))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- Phase 9B: employee-only endpoints ------------------------------

    private static final List<String> TELLER_PERMISSIONS =
            List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL");

    private EmployeeDepositResponse sampleEmployeeDepositResponse(UUID accountId, BigDecimal newBalance) {
        return new EmployeeDepositResponse(
                new AccountResponse(accountId, UUID.randomUUID(), "617242043877", "BANK0000001", AccountType.SAVINGS,
                        newBalance, "INR", AccountStatus.ACTIVE, Instant.now(), Instant.now()),
                "TXN-ABC123");
    }

    @Test
    void employeeLookupByAccountNumber_returns200_whenCallerIsEmployeeWithAccountView() throws Exception {
        when(accountService.employeeLookupByAccountNumber("617242043877"))
                .thenReturn(sampleResponse(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("20000.00")));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(get("/api/v1/accounts/employee-lookup")
                        .header("Authorization", "Bearer teller-token")
                        .param("accountNumber", "617242043877"))
                .andExpect(status().isOk());
    }

    @Test
    void employeeLookupByAccountNumber_returns403_whenCallerIsACustomerToken() throws Exception {
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/accounts/employee-lookup")
                        .header("Authorization", "Bearer valid-token")
                        .param("accountNumber", "617242043877"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeLookupByAccountNumber_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/employee-lookup").param("accountNumber", "617242043877"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeLookupByCustomerId_returns200_forEmployeeWithAccountView() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(accountService.employeeLookupByCustomerId(customerId)).thenReturn(List.of());
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(get("/api/v1/accounts/employee-lookup/customer/{customerId}", customerId)
                        .header("Authorization", "Bearer teller-token"))
                .andExpect(status().isOk());
    }

    @Test
    void employeeDeposit_returns201_whenCallerIsTeller() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.employeeDeposit(eq(accountId), any(), any(), eq("teller-token")))
                .thenReturn(sampleEmployeeDepositResponse(accountId, new BigDecimal("30000.00")));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), "CASH DEPOSIT - Branch HQ001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TXN-ABC123"))
                .andExpect(jsonPath("$.account.balance").value(30000.00));
    }

    @ParameterizedTest(name = "{0} lacks CASH_DEPOSIT and is rejected from the employee-deposit endpoint")
    @ValueSource(strings = {"KYC_OFFICER", "LOAN_OFFICER", "CARD_OFFICER", "OPERATIONS", "ADMIN"})
    void employeeDeposit_returns403_forRolesWithoutCashDeposit(String role) throws Exception {
        UUID accountId = UUID.randomUUID();
        authenticateAsEmployee("role-token", role, List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"), AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer role-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeDeposit_returns403_whenCallerIsACustomerToken() throws Exception {
        UUID accountId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeDeposit_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeDeposit_returns400_whenAmountIsZero() throws Exception {
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", UUID.randomUUID())
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(BigDecimal.ZERO, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void employeeDeposit_returns404_whenAccountDoesNotExist() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(accountService.employeeDeposit(eq(missingId), any(), any(), any())).thenThrow(new AccountNotFoundException(missingId));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", missingId)
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeDeposit_returns422_whenAccountIsNotActive() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.employeeDeposit(eq(accountId), any(), any(), any()))
                .thenThrow(new AccountNotActiveException(accountId, AccountStatus.INACTIVE));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void employeeDeposit_returns403_whenBranchScopeIsViolated() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.employeeDeposit(eq(accountId), any(), any(), any()))
                .thenThrow(new BranchScopeViolationException(accountId, "BANK0000001", "HDFC0001234"));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, "HDFC0001234");

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeDeposit_returns409_onOptimisticLockConflict() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accountService.employeeDeposit(eq(accountId), any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(AccountResponse.class, accountId.toString()));
        authenticateAsEmployee("teller-token", "TELLER", TELLER_PERMISSIONS, AccountServiceImpl.BANKSPHERE_IFSC);

        mockMvc.perform(post("/api/v1/accounts/{id}/employee-deposit", accountId)
                        .header("Authorization", "Bearer teller-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AmountRequest(new BigDecimal("10000.00"), null))))
                .andExpect(status().isConflict());
    }
}
