package com.banksphere.employee.service;

import com.banksphere.employee.dto.EmployeeLoginRequest;
import com.banksphere.employee.dto.EmployeeLoginResponse;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.entity.Branch;
import com.banksphere.employee.entity.BranchStatus;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.exception.EmployeeNotFoundException;
import com.banksphere.employee.exception.InvalidCredentialsException;
import com.banksphere.employee.repository.EmployeeRepository;
import com.banksphere.employee.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAuthServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmployeeAuditLog auditLog;

    private EmployeeAuthServiceImpl authService;
    private Branch branch;

    @BeforeEach
    void setUp() {
        authService = new EmployeeAuthServiceImpl(employeeRepository, passwordEncoder, jwtService, auditLog);
        branch = Branch.builder().id(UUID.randomUUID()).branchCode("HQ001").branchName("Head Office")
                .ifsc("BANK0000001").status(BranchStatus.ACTIVE).build();
    }

    private Employee activeEmployee(EmployeeStatus status) {
        return Employee.builder().id(UUID.randomUUID()).employeeNumber("EMP000123").username("jane.teller")
                .passwordHash("stored-hash").firstName("Jane").lastName("Teller")
                .email("jane.teller@banksphere.example").branch(branch).status(status)
                .roles(Set.of(Role.TELLER)).build();
    }

    @Test
    void login_succeeds_andReturnsTokenAndEmployeeInfo_whenCredentialsAreValidAndEmployeeIsActive() {
        Employee employee = activeEmployee(EmployeeStatus.ACTIVE);
        when(employeeRepository.findByUsername("jane.teller")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken(employee)).thenReturn("a-real-jwt");
        when(jwtService.expirationSeconds()).thenReturn(1800L);

        EmployeeLoginResponse response = authService.login(new EmployeeLoginRequest("jane.teller", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("a-real-jwt");
        assertThat(response.employee().username()).isEqualTo("jane.teller");
        assertThat(response.roles()).containsExactly("TELLER");
        assertThat(response.permissions()).contains("CASH_DEPOSIT", "CASH_WITHDRAWAL");
        assertThat(response.branch().id()).isEqualTo(branch.getId());
    }

    @Test
    void login_throwsInvalidCredentialsException_whenPasswordIsWrong() {
        Employee employee = activeEmployee(EmployeeStatus.ACTIVE);
        when(employeeRepository.findByUsername("jane.teller")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new EmployeeLoginRequest("jane.teller", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_throwsInvalidCredentialsException_whenUsernameDoesNotExist() {
        when(employeeRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new EmployeeLoginRequest("nobody", "whatever-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_throwsInvalidCredentialsException_whenEmployeeIsInactive() {
        Employee employee = activeEmployee(EmployeeStatus.INACTIVE);
        when(employeeRepository.findByUsername("jane.teller")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new EmployeeLoginRequest("jane.teller", "correct-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_throwsInvalidCredentialsException_whenEmployeeIsLocked() {
        Employee employee = activeEmployee(EmployeeStatus.LOCKED);
        when(employeeRepository.findByUsername("jane.teller")).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new EmployeeLoginRequest("jane.teller", "correct-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void getCurrentEmployee_returnsEmployee_whenFound() {
        Employee employee = activeEmployee(EmployeeStatus.ACTIVE);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        EmployeeResponse response = authService.getCurrentEmployee(employee.getId());

        assertThat(response.id()).isEqualTo(employee.getId());
        assertThat(response.username()).isEqualTo("jane.teller");
    }

    @Test
    void getCurrentEmployee_throwsEmployeeNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentEmployee(id)).isInstanceOf(EmployeeNotFoundException.class);
    }

    /**
     * Structural proof, not a redaction check: EmployeeResponse simply has
     * no field capable of carrying a password hash, so there is nothing to
     * accidentally serialize — see CLAUDE.md's password-DTO rule and
     * EmployeeResponse's own javadoc.
     */
    @Test
    void employeeResponse_hasNoFieldCapableOfCarryingAPasswordOrHash() {
        for (RecordComponent component : EmployeeResponse.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();
            assertThat(name).doesNotContain("password").doesNotContain("hash").doesNotContain("secret");
        }
    }
}
