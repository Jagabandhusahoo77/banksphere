package com.banksphere.customer.controller;

import com.banksphere.customer.dto.CustomerCreateRequest;
import com.banksphere.customer.dto.CustomerLookupResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.entity.CustomerStatus;
import com.banksphere.customer.exception.CustomerAccessDeniedException;
import com.banksphere.customer.exception.CustomerNotFoundException;
import com.banksphere.customer.security.EmployeeJwtValidator;
import com.banksphere.customer.security.JwtAccessDeniedHandler;
import com.banksphere.customer.security.JwtAuthenticationEntryPoint;
import com.banksphere.customer.security.JwtService;
import com.banksphere.customer.security.SecurityConfig;
import com.banksphere.customer.service.CustomerService;
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
import java.time.LocalDate;
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

/**
 * The real security filter chain runs in this test: {@code SecurityConfig}
 * is explicitly {@code @Import}ed (a plain {@code @WebMvcTest} slice does
 * NOT auto-detect a custom {@code @EnableWebSecurity} configuration class,
 * so without this import Spring Boot silently falls back to its own
 * auto-configured default chain — CSRF-protected, form-login-based —
 * which is why POSTs failed with an unexplained 403 and GETs with 401
 * before this was added). {@code JwtAuthenticationEntryPoint}/
 * {@code JwtAccessDeniedHandler} are imported alongside it since
 * {@code SecurityConfig} depends on them and they're plain
 * {@code @Component}s, not part of {@code @WebMvcTest}'s default slice.
 * {@code JwtAuthenticationFilter} itself IS auto-included (Filter beans
 * are part of the slice); only its {@code JwtService} dependency is
 * mocked, so a test's "logged in as" identity comes from what
 * {@code jwtService.parseClaims(...)} is stubbed to return, exercised
 * through the real filter chain exactly as production would. This is
 * deliberately NOT done via {@code addFilters = false} + a
 * manually-supplied Authentication: that combination was tried first and
 * failed, because disabling the filter chain also disables the Spring
 * Security filter that populates {@code HttpServletRequest.getUserPrincipal()}
 * — which is what resolves a plain {@code Authentication} controller
 * parameter — leaving it null and causing a NullPointerException (seen as
 * an unexplained 500) instead of the intended 401/403.
 */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EmployeeJwtValidator employeeJwtValidator;

    private void authenticateAs(UUID customerId) {
        Claims claims = Jwts.claims().subject(customerId.toString()).build();
        when(jwtService.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    /** Phase 9B — same pattern as the other services' employee-token tests. */
    private void authenticateAsEmployee(String token, List<String> permissions) {
        Claims claims = Jwts.claims()
                .subject(UUID.randomUUID().toString())
                .add("principalType", "EMPLOYEE")
                .add("employeeNumber", "EMP000010")
                .add("permissions", permissions)
                .build();
        when(employeeJwtValidator.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    @Test
    void createCustomer_returns201_whenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St");
        CustomerResponse response = new CustomerResponse(id, "Jane", "Doe", "jane.doe@example.com",
                "+1-555-0100", LocalDate.of(1990, 5, 20), "123 Main St", CustomerStatus.ACTIVE,
                Instant.now(), Instant.now());

        when(customerService.createCustomer(any())).thenReturn(response);
        authenticateAs(id);

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"));
    }

    @Test
    void createCustomer_returns401_whenNoTokenSupplied() throws Exception {
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Jane", "Doe", "jane.doe@example.com", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St");

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCustomer_returns400_whenEmailIsInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Jane", "Doe", "not-an-email", "+1-555-0100",
                LocalDate.of(1990, 5, 20), "123 Main St");
        authenticateAs(id);

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCustomer_returns200_whenFoundAndRequestingOwnProfile() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerResponse response = new CustomerResponse(id, "Jane", "Doe", "jane.doe@example.com",
                "+1-555-0100", LocalDate.of(1990, 5, 20), "123 Main St", CustomerStatus.ACTIVE,
                Instant.now(), Instant.now());
        when(customerService.getCustomer(eq(id), eq(id))).thenReturn(response);
        authenticateAs(id);

        mockMvc.perform(get("/api/v1/customers/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getCustomer_returns401_whenNoTokenSupplied() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/customers/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomer_returns401_whenTokenIsMalformed() throws Exception {
        UUID id = UUID.randomUUID();
        when(jwtService.parseClaims("garbage")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/{id}", id).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomer_returns404_whenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerService.getCustomer(eq(id), eq(id))).thenThrow(new CustomerNotFoundException(id));
        authenticateAs(id);

        mockMvc.perform(get("/api/v1/customers/{id}", id).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCustomer_returns403_whenRequestingAnotherCustomersProfile() throws Exception {
        UUID requestedId = UUID.randomUUID();
        UUID requestingId = UUID.randomUUID();
        when(customerService.getCustomer(eq(requestedId), eq(requestingId)))
                .thenThrow(new CustomerAccessDeniedException(requestedId));
        authenticateAs(requestingId);

        mockMvc.perform(get("/api/v1/customers/{id}", requestedId).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    // ---- Phase 9B: employee-lookup endpoint -----------------------------

    @Test
    void employeeLookup_returns200_whenCallerIsEmployeeWithCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.employeeLookup(customerId))
                .thenReturn(new CustomerLookupResponse(customerId, "John", "Smith", CustomerStatus.ACTIVE));
        authenticateAsEmployee("teller-token", List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}", customerId).header("Authorization", "Bearer teller-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Smith"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist());
    }

    @Test
    void employeeLookup_returns403_whenEmployeeLacksCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAsEmployee("no-permission-token", List.of("EMPLOYEE_MANAGE"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}", customerId).header("Authorization", "Bearer no-permission-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeLookup_returns403_whenCallerIsACustomerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}", customerId).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeLookup_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeLookup_returns404_whenCustomerDoesNotExist() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.employeeLookup(customerId)).thenThrow(new CustomerNotFoundException(customerId));
        authenticateAsEmployee("teller-token", List.of("CUSTOMER_VIEW"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}", customerId).header("Authorization", "Bearer teller-token"))
                .andExpect(status().isNotFound());
    }

    // ---- Phase 9C: employee-lookup full profile (Customer 360) ---------

    @Test
    void employeeLookupFullProfile_returns200_withFullProfile_whenCallerIsEmployeeWithCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.employeeLookupFullProfile(customerId))
                .thenReturn(new CustomerResponse(customerId, "John", "Smith", "john@example.com", "+1-555-0100",
                        LocalDate.of(1985, 3, 12), "1 Test St", CustomerStatus.ACTIVE, Instant.now(), Instant.now()));
        authenticateAsEmployee("kyc-officer-token", List.of("CUSTOMER_VIEW"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}/profile", customerId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.address").value("1 Test St"));
    }

    @Test
    void employeeLookupFullProfile_returns403_whenEmployeeLacksCustomerView() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAsEmployee("no-permission-token", List.of("EMPLOYEE_MANAGE"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}/profile", customerId)
                        .header("Authorization", "Bearer no-permission-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeLookupFullProfile_returns403_whenCallerIsACustomerToken() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAs(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}/profile", customerId)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeLookupFullProfile_returns404_whenCustomerDoesNotExist() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customerService.employeeLookupFullProfile(customerId)).thenThrow(new CustomerNotFoundException(customerId));
        authenticateAsEmployee("teller-token", List.of("CUSTOMER_VIEW"));

        mockMvc.perform(get("/api/v1/customers/employee-lookup/{id}/profile", customerId)
                        .header("Authorization", "Bearer teller-token"))
                .andExpect(status().isNotFound());
    }

    /**
     * Regression: an employee token must never be able to satisfy the
     * SELF-only ownership check on the original endpoint. It doesn't get
     * a clean 403 here — {@code CurrentUser.id()} tries {@code
     * UUID.fromString()} on the (non-UUID-shaped) EmployeePrincipal and
     * fails, which the existing {@code IllegalArgumentException} handler
     * turns into a 400 — but the key property this test protects is that
     * it fails, and fails without ever reaching the customer lookup or
     * returning another customer's real data. See ADR-007's documented
     * limitation on this rough edge.
     */
    @Test
    void getCustomer_isRejected_whenCallerIsAnEmployeeToken_notMisreadAsACustomerId() throws Exception {
        UUID customerId = UUID.randomUUID();
        authenticateAsEmployee("teller-token", List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"));

        mockMvc.perform(get("/api/v1/customers/{id}", customerId).header("Authorization", "Bearer teller-token"))
                .andExpect(status().isBadRequest());
    }
}
