package com.banksphere.employee.controller;

import com.banksphere.employee.dto.BranchSummary;
import com.banksphere.employee.dto.EmployeeLoginRequest;
import com.banksphere.employee.dto.EmployeeLoginResponse;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.EmployeeSummary;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.exception.InvalidCredentialsException;
import com.banksphere.employee.security.JwtAccessDeniedHandler;
import com.banksphere.employee.security.JwtAuthenticationEntryPoint;
import com.banksphere.employee.security.JwtService;
import com.banksphere.employee.security.SecurityConfig;
import com.banksphere.employee.service.EmployeeAuthService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Same pattern every other service's controller test uses (see CLAUDE.md):
 * the real JwtAuthenticationFilter/SecurityConfig run here — only
 * JwtService's token parsing is mocked, via {@code @Import(SecurityConfig...)}
 * rather than {@code addFilters = false}.
 */
@WebMvcTest(EmployeeAuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class EmployeeAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmployeeAuthService employeeAuthService;

    @MockBean
    private JwtService jwtService;

    private void authenticateAs(UUID employeeId) {
        Claims claims = Jwts.claims()
                .subject(employeeId.toString())
                .add("principalType", "EMPLOYEE")
                .add("roles", List.of("TELLER"))
                .add("permissions", List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"))
                .add("employeeNumber", "EMP000123")
                .add("branchId", UUID.randomUUID().toString())
                .build();
        when(jwtService.parseClaims("valid-token")).thenReturn(Optional.of(claims));
    }

    private EmployeeResponse sampleEmployeeResponse(UUID id) {
        return new EmployeeResponse(id, "EMP000123", "jane.teller", "Jane", "Teller",
                "jane.teller@banksphere.example", List.of("TELLER"), List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW"),
                new BranchSummary(UUID.randomUUID(), "HQ001", "Head Office", "BANK0000001"),
                EmployeeStatus.ACTIVE, Instant.now(), Instant.now());
    }

    @Test
    void login_returns200WithTokenAndEmployeeInfo_whenCredentialsAreValid() throws Exception {
        EmployeeLoginResponse response = EmployeeLoginResponse.of(
                "a-real-jwt", 1800L,
                new EmployeeSummary(UUID.randomUUID(), "EMP000123", "jane.teller", "Jane", "Teller",
                        "jane.teller@banksphere.example", EmployeeStatus.ACTIVE),
                List.of("TELLER"), List.of("CUSTOMER_VIEW"),
                new BranchSummary(UUID.randomUUID(), "HQ001", "Head Office", "BANK0000001"));
        when(employeeAuthService.login(new EmployeeLoginRequest("jane.teller", "correct-password"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/employees/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeLoginRequest("jane.teller", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("a-real-jwt"))
                .andExpect(jsonPath("$.employee.username").value("jane.teller"))
                .andExpect(jsonPath("$.roles[0]").value("TELLER"));
    }

    @Test
    void login_isPubliclyReachable_withoutAnyToken() throws Exception {
        when(employeeAuthService.login(new EmployeeLoginRequest("jane.teller", "correct-password")))
                .thenReturn(EmployeeLoginResponse.of("token", 1800L,
                        new EmployeeSummary(UUID.randomUUID(), "EMP000123", "jane.teller", "Jane", "Teller",
                                "jane.teller@banksphere.example", EmployeeStatus.ACTIVE),
                        List.of("TELLER"), List.of(), new BranchSummary(UUID.randomUUID(), "HQ001", "Head Office", "BANK0000001")));

        mockMvc.perform(post("/api/v1/employees/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeLoginRequest("jane.teller", "correct-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void login_returns401_whenCredentialsAreInvalid() throws Exception {
        when(employeeAuthService.login(new EmployeeLoginRequest("jane.teller", "wrong-password")))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/employees/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeLoginRequest("jane.teller", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns400_whenUsernameIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/employees/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeLoginRequest("", "password"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_returns200WithCallersOwnProfile_whenTokenIsValid() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeAuthService.getCurrentEmployee(employeeId)).thenReturn(sampleEmployeeResponse(employeeId));
        authenticateAs(employeeId);

        mockMvc.perform(get("/api/v1/employees/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_returns401_whenNoTokenSupplied() throws Exception {
        mockMvc.perform(get("/api/v1/employees/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns401_whenTokenIsMalformed() throws Exception {
        when(jwtService.parseClaims("garbage")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/employees/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Models a token that verified (right key, well-formed) but was never
     * marked as an employee token — the defense-in-depth layer described in
     * JwtService's javadoc. In practice a real customer JWT never reaches
     * this state at all (it fails parseClaims outright, see JwtServiceTest's
     * cross-key test); this proves the second, independent layer also
     * rejects it if it somehow did.
     */
    @Test
    void me_returns401_whenTokenDoesNotDeclareItselfAnEmployeeToken() throws Exception {
        Claims nonEmployeeClaims = Jwts.claims().subject(UUID.randomUUID().toString()).build();
        when(jwtService.parseClaims("customer-token")).thenReturn(Optional.of(nonEmployeeClaims));

        mockMvc.perform(get("/api/v1/employees/me").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isUnauthorized());
    }
}
