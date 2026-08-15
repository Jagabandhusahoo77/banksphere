package com.banksphere.employee.service;

import com.banksphere.employee.dto.CreateEmployeeRequest;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.UpdateEmployeeStatusRequest;
import com.banksphere.employee.entity.Branch;
import com.banksphere.employee.entity.BranchStatus;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.exception.BranchNotFoundException;
import com.banksphere.employee.exception.DuplicateEmployeeNumberException;
import com.banksphere.employee.exception.DuplicateUsernameException;
import com.banksphere.employee.exception.EmployeeNotFoundException;
import com.banksphere.employee.repository.BranchRepository;
import com.banksphere.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmployeeAuditLog auditLog;

    private EmployeeServiceImpl employeeService;
    private UUID actingEmployeeId;
    private Branch branch;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeServiceImpl(employeeRepository, branchRepository, passwordEncoder, auditLog);
        actingEmployeeId = UUID.randomUUID();
        branch = Branch.builder().id(UUID.randomUUID()).branchCode("HQ001").branchName("Head Office")
                .ifsc("BANK0000001").status(BranchStatus.ACTIVE).build();
    }

    private CreateEmployeeRequest validCreateRequest() {
        return new CreateEmployeeRequest("EMP000123", "jane.teller", "Password123", "Jane", "Teller",
                "jane.teller@banksphere.example", branch.getId(), Set.of(Role.TELLER));
    }

    @Test
    void createEmployee_savesAndReturnsEmployee_whenRequestIsValid() {
        when(employeeRepository.existsByEmployeeNumber("EMP000123")).thenReturn(false);
        when(employeeRepository.existsByUsername("jane.teller")).thenReturn(false);
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(passwordEncoder.encode("Password123")).thenReturn("hashed-password");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeResponse response = employeeService.createEmployee(validCreateRequest(), actingEmployeeId);

        assertThat(response.employeeNumber()).isEqualTo("EMP000123");
        assertThat(response.username()).isEqualTo("jane.teller");
        assertThat(response.roles()).containsExactly("TELLER");
        assertThat(response.branch().id()).isEqualTo(branch.getId());
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void createEmployee_hashesPasswordBeforeSaving_neverStoresPlaintext() {
        when(employeeRepository.existsByEmployeeNumber(anyString())).thenReturn(false);
        when(employeeRepository.existsByUsername(anyString())).thenReturn(false);
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$definitely-not-the-raw-password");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        employeeService.createEmployee(validCreateRequest(), actingEmployeeId);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$definitely-not-the-raw-password");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Password123");
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void createEmployee_assignsAllRequestedRoles() {
        when(employeeRepository.existsByEmployeeNumber(anyString())).thenReturn(false);
        when(employeeRepository.existsByUsername(anyString())).thenReturn(false);
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP000124", "manager.jane", "Password123",
                "Jane", "Manager", "jane.manager@banksphere.example", branch.getId(),
                Set.of(Role.BRANCH_MANAGER, Role.TELLER));

        EmployeeResponse response = employeeService.createEmployee(request, actingEmployeeId);

        assertThat(response.roles()).containsExactlyInAnyOrder("BRANCH_MANAGER", "TELLER");
    }

    @Test
    void createEmployee_throwsDuplicateEmployeeNumberException_whenEmployeeNumberAlreadyExists() {
        when(employeeRepository.existsByEmployeeNumber("EMP000123")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(validCreateRequest(), actingEmployeeId))
                .isInstanceOf(DuplicateEmployeeNumberException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createEmployee_throwsDuplicateUsernameException_whenUsernameAlreadyExists() {
        when(employeeRepository.existsByEmployeeNumber("EMP000123")).thenReturn(false);
        when(employeeRepository.existsByUsername("jane.teller")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(validCreateRequest(), actingEmployeeId))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createEmployee_throwsBranchNotFoundException_whenBranchDoesNotExist() {
        UUID unknownBranchId = UUID.randomUUID();
        when(employeeRepository.existsByEmployeeNumber(anyString())).thenReturn(false);
        when(employeeRepository.existsByUsername(anyString())).thenReturn(false);
        when(branchRepository.findById(unknownBranchId)).thenReturn(Optional.empty());

        CreateEmployeeRequest request = new CreateEmployeeRequest("EMP000123", "jane.teller", "Password123",
                "Jane", "Teller", "jane.teller@banksphere.example", unknownBranchId, Set.of(Role.TELLER));

        assertThatThrownBy(() -> employeeService.createEmployee(request, actingEmployeeId))
                .isInstanceOf(BranchNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void listEmployees_returnsEveryEmployeeMappedToResponse() {
        Employee employee = Employee.builder().id(UUID.randomUUID()).employeeNumber("EMP000123")
                .username("jane.teller").passwordHash("hash").firstName("Jane").lastName("Teller")
                .email("jane.teller@banksphere.example").branch(branch).status(EmployeeStatus.ACTIVE)
                .roles(Set.of(Role.TELLER)).build();
        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        List<EmployeeResponse> responses = employeeService.listEmployees();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).employeeNumber()).isEqualTo("EMP000123");
    }

    @Test
    void getEmployee_returnsResponse_whenFound() {
        UUID id = UUID.randomUUID();
        Employee employee = Employee.builder().id(id).employeeNumber("EMP000123").username("jane.teller")
                .passwordHash("hash").firstName("Jane").lastName("Teller").email("jane.teller@banksphere.example")
                .branch(branch).status(EmployeeStatus.ACTIVE).roles(Set.of(Role.TELLER)).build();
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.getEmployee(id);

        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    void getEmployee_throwsEmployeeNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getEmployee(id)).isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void updateStatus_updatesAndReturnsEmployee_whenFound() {
        UUID id = UUID.randomUUID();
        Employee employee = Employee.builder().id(id).employeeNumber("EMP000123").username("jane.teller")
                .passwordHash("hash").firstName("Jane").lastName("Teller").email("jane.teller@banksphere.example")
                .branch(branch).status(EmployeeStatus.ACTIVE).roles(Set.of(Role.TELLER)).build();
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeResponse response = employeeService.updateStatus(id, new UpdateEmployeeStatusRequest(EmployeeStatus.LOCKED), actingEmployeeId);

        assertThat(response.status()).isEqualTo(EmployeeStatus.LOCKED);
    }

    @Test
    void updateStatus_throwsEmployeeNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateStatus(id, new UpdateEmployeeStatusRequest(EmployeeStatus.LOCKED), actingEmployeeId))
                .isInstanceOf(EmployeeNotFoundException.class);
    }
}
