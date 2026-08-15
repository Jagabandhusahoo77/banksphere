package com.banksphere.employee.service;

import com.banksphere.employee.dto.CreateEmployeeRequest;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.UpdateEmployeeStatusRequest;
import com.banksphere.employee.entity.Branch;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.exception.BranchNotFoundException;
import com.banksphere.employee.exception.DuplicateEmployeeNumberException;
import com.banksphere.employee.exception.DuplicateUsernameException;
import com.banksphere.employee.exception.EmployeeNotFoundException;
import com.banksphere.employee.repository.BranchRepository;
import com.banksphere.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeAuditLog auditLog;

    @Override
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request, UUID actingEmployeeId) {
        if (employeeRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw new DuplicateEmployeeNumberException(request.employeeNumber());
        }
        if (employeeRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new BranchNotFoundException(request.branchId()));

        Employee employee = Employee.builder()
                .employeeNumber(request.employeeNumber())
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .branch(branch)
                .roles(request.roles())
                .build();
        Employee saved = employeeRepository.save(employee);

        auditLog.employeeCreated(actingEmployeeId.toString(), saved.getId().toString(), saved.getEmployeeNumber());
        return EmployeeResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees() {
        return employeeRepository.findAll().stream().map(EmployeeResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(UUID id) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        return EmployeeResponse.from(employee);
    }

    @Override
    @Transactional
    public EmployeeResponse updateStatus(UUID id, UpdateEmployeeStatusRequest request, UUID actingEmployeeId) {
        Employee employee = employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
        employee.setStatus(request.status());
        Employee saved = employeeRepository.save(employee);

        auditLog.employeeStatusChanged(actingEmployeeId.toString(), saved.getId().toString(), saved.getStatus().name());
        return EmployeeResponse.from(saved);
    }
}
