package com.banksphere.employee.controller;

import com.banksphere.employee.dto.Customer360Response;
import com.banksphere.employee.dto.Customer360Section;
import com.banksphere.employee.dto.CustomerProfileLookupResult;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.security.JwtAccessDeniedHandler;
import com.banksphere.employee.security.JwtAuthenticationEntryPoint;
import com.banksphere.employee.security.JwtService;
import com.banksphere.employee.security.RolePermissions;
import com.banksphere.employee.security.SecurityConfig;
import com.banksphere.employee.service.Customer360Service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** See OperationsControllerTest's javadoc for why the real security filter chain runs (never {@code addFilters = false}). */
@WebMvcTest(Customer360Controller.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class Customer360ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Customer360Service customer360Service;

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

    private Customer360Response sampleResponse(UUID customerId) {
        return new Customer360Response(
                customerId,
                Customer360Section.of(new CustomerProfileLookupResult(customerId, "John", "Smith", "john@example.com", "+1-555", "ACTIVE", Instant.now())),
                Customer360Section.of(List.of()),
                Customer360Section.of(List.of()),
                Customer360Section.of(List.of()),
                Customer360Section.unavailable("Requires KYC_VIEW"),
                List.of("LOANS", "CARDS", "FOREX", "SERVICE_REQUESTS"));
    }

    @Test
    void getCustomer360_returns200_forKycOfficer() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customer360Service.getCustomer360(eq(customerId), any(), any())).thenReturn(sampleResponse(customerId));
        authenticateAs("kyc-officer-token", Role.KYC_OFFICER);

        mockMvc.perform(get("/api/v1/employee/customers/{customerId}/360", customerId)
                        .header("Authorization", "Bearer kyc-officer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.available").value(true))
                .andExpect(jsonPath("$.unavailableCapabilities[0]").value("LOANS"));
    }

    @Test
    void getCustomer360_returns200_forEveryOperationalRole_sinceEachHoldsAtLeastOneViewPermission() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customer360Service.getCustomer360(eq(customerId), any(), any())).thenReturn(sampleResponse(customerId));

        for (Role role : Role.values()) {
            authenticateAs("token-" + role.name(), role);
            mockMvc.perform(get("/api/v1/employee/customers/{customerId}/360", customerId)
                            .header("Authorization", "Bearer token-" + role.name()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void getCustomer360_forwardsCallerPermissionSet_toTheAggregationService() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(customer360Service.getCustomer360(eq(customerId), any(), any())).thenReturn(sampleResponse(customerId));
        authenticateAs("admin-token", Role.ADMIN);

        mockMvc.perform(get("/api/v1/employee/customers/{customerId}/360", customerId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Set> captor = org.mockito.ArgumentCaptor.forClass(Set.class);
        org.mockito.Mockito.verify(customer360Service).getCustomer360(eq(customerId), captor.capture(), any());
        Set<String> forwarded = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(forwarded).contains("CUSTOMER_VIEW", "ACCOUNT_VIEW");
        org.assertj.core.api.Assertions.assertThat(forwarded).doesNotContain("KYC_VIEW", "TRANSACTION_VIEW");
    }

    @Test
    void getCustomer360_returns401_whenTokenDoesNotParseAsAValidEmployeeToken() throws Exception {
        // employee-service has never accepted a customer-signed token at
        // all (unlike account/transaction/beneficiary/kyc-service's dual
        // JWT model) — an arbitrary bearer token that doesn't verify
        // against JwtService is simply unauthenticated.
        mockMvc.perform(get("/api/v1/employee/customers/{customerId}/360", UUID.randomUUID())
                        .header("Authorization", "Bearer some-other-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomer360_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/employee/customers/{customerId}/360", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
