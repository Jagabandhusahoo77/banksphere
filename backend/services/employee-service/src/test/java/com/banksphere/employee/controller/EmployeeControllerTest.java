package com.banksphere.employee.controller;

import com.banksphere.employee.dto.BranchSummary;
import com.banksphere.employee.dto.CreateEmployeeRequest;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.UpdateEmployeeStatusRequest;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.security.JwtAccessDeniedHandler;
import com.banksphere.employee.security.JwtAuthenticationEntryPoint;
import com.banksphere.employee.security.JwtService;
import com.banksphere.employee.security.RolePermissions;
import com.banksphere.employee.security.SecurityConfig;
import com.banksphere.employee.service.EmployeeService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The RBAC-focused suite: every endpoint here requires {@code
 * EMPLOYEE_MANAGE}, held only by {@code ADMIN} in this phase's role table
 * (see {@code RolePermissions}). {@code employeeAsRole(...)} builds a real
 * token-shaped {@link Claims} object carrying exactly the permission set
 * {@code RolePermissions} would compute for that role — so this suite is
 * pinned to the same authoritative mapping {@code RolePermissionsTest}
 * checks, not a hand-maintained duplicate of it.
 */
@WebMvcTest(EmployeeController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private JwtService jwtService;

    private void authenticateAs(String token, UUID employeeId, Role role) {
        List<String> permissions = RolePermissions.permissionsFor(role).stream().map(Enum::name).toList();
        Claims claims = Jwts.claims()
                .subject(employeeId.toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of(role.name()))
                .add("permissions", permissions)
                .add("employeeNumber", "EMP000123")
                .add("branchId", UUID.randomUUID().toString())
                .build();
        when(jwtService.parseClaims(token)).thenReturn(Optional.of(claims));
    }

    private EmployeeResponse sampleEmployeeResponse(UUID id) {
        return new EmployeeResponse(id, "EMP000200", "new.hire", "New", "Hire",
                "new.hire@banksphere.example", List.of("TELLER"), List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"),
                new BranchSummary(UUID.randomUUID(), "HQ001", "Head Office", "BANK0000001"),
                EmployeeStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private CreateEmployeeRequest validCreateRequest() {
        return new CreateEmployeeRequest("EMP000200", "new.hire", "Password123", "New", "Hire",
                "new.hire@banksphere.example", UUID.randomUUID(), Set.of(Role.TELLER));
    }

    @Test
    void createEmployee_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(post("/api/v1/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createEmployee_returns201_whenCallerIsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.createEmployee(any(), any())).thenReturn(sampleEmployeeResponse(id));
        authenticateAs("admin-token", UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated());
    }

    /**
     * Tests 13-18 of this phase's own required scenarios: every
     * non-{@code ADMIN} role lacks {@code EMPLOYEE_MANAGE} in the
     * authoritative table, so every one of them must be rejected here — a
     * single parameterized proof against the real mapping rather than six
     * near-duplicate hand-written tests that could silently drift from it.
     */
    @ParameterizedTest(name = "{0} lacks EMPLOYEE_MANAGE and is rejected from creating an employee")
    @EnumSource(value = Role.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void createEmployee_returns403_forEveryNonAdminRole(Role role) throws Exception {
        authenticateAs("role-token", UUID.randomUUID(), role);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", "Bearer role-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listEmployees_returns200_whenCallerIsAdmin() throws Exception {
        when(employeeService.listEmployees()).thenReturn(List.of(sampleEmployeeResponse(UUID.randomUUID())));
        authenticateAs("admin-token", UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(get("/api/v1/employees").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void listEmployees_returns403_whenCallerIsTeller() throws Exception {
        authenticateAs("teller-token", UUID.randomUUID(), Role.TELLER);

        mockMvc.perform(get("/api/v1/employees").header("Authorization", "Bearer teller-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 21 of this phase's own required scenarios: a non-admin employee,
     * even when the id in the URL happens to belong to a real colleague (not
     * even necessarily themselves), is rejected before that id is ever
     * looked up — the permission check runs first.
     */
    @Test
    void getEmployee_returns403_whenNonAdminRequestsAnotherEmployeesProtectedProfile() throws Exception {
        UUID someColleaguesId = UUID.randomUUID();
        authenticateAs("teller-token", UUID.randomUUID(), Role.TELLER);

        mockMvc.perform(get("/api/v1/employees/{id}", someColleaguesId).header("Authorization", "Bearer teller-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEmployee_returns200_whenCallerIsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.getEmployee(id)).thenReturn(sampleEmployeeResponse(id));
        authenticateAs("admin-token", UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(get("/api/v1/employees/{id}", id).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_returns403_whenCallerIsNotAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        authenticateAs("branch-manager-token", UUID.randomUUID(), Role.BRANCH_MANAGER);

        mockMvc.perform(put("/api/v1/employees/{id}/status", id)
                        .header("Authorization", "Bearer branch-manager-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEmployeeStatusRequest(EmployeeStatus.LOCKED))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_returns200_whenCallerIsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(employeeService.updateStatus(any(), any(), any())).thenReturn(sampleEmployeeResponse(id));
        authenticateAs("admin-token", UUID.randomUUID(), Role.ADMIN);

        mockMvc.perform(put("/api/v1/employees/{id}/status", id)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateEmployeeStatusRequest(EmployeeStatus.LOCKED))))
                .andExpect(status().isOk());
    }
}
