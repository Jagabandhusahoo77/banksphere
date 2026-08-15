package com.banksphere.employee.service;

import com.banksphere.employee.dto.BranchSummary;
import com.banksphere.employee.dto.EmployeeLoginRequest;
import com.banksphere.employee.dto.EmployeeLoginResponse;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.EmployeeSummary;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Permission;
import com.banksphere.employee.exception.EmployeeNotFoundException;
import com.banksphere.employee.exception.InvalidCredentialsException;
import com.banksphere.employee.repository.EmployeeRepository;
import com.banksphere.employee.security.JwtService;
import com.banksphere.employee.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeAuthServiceImpl implements EmployeeAuthService {

    /**
     * Same timing-safe-dummy-hash pattern as customer-service's AuthServiceImpl
     * — see that class's javadoc for the full rationale. Applied here even
     * though employee registration is admin-only (no public sign-up to
     * enumerate against): a username, once known/guessed by an attacker
     * (e.g. a former employee's), should still not be distinguishable via
     * response timing from one that never existed.
     */
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("dummy-password-for-timing-safety-only");

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmployeeAuditLog auditLog;

    @Override
    @Transactional(readOnly = true)
    public EmployeeLoginResponse login(EmployeeLoginRequest request) {
        Optional<Employee> employeeOpt = employeeRepository.findByUsername(request.username());

        String hashToCheck = employeeOpt.map(Employee::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        boolean credentialsValid = employeeOpt.isPresent()
                && employeeOpt.get().getStatus() == EmployeeStatus.ACTIVE
                && passwordMatches;

        if (!credentialsValid) {
            // Never distinguish "no such username" from "wrong password" from
            // "inactive" from "locked" in the response — see InvalidCredentialsException.
            auditLog.loginFailed(request.username());
            throw new InvalidCredentialsException();
        }

        Employee employee = employeeOpt.get();
        auditLog.loginSucceeded(employee.getId().toString(), employee.getEmployeeNumber());

        String token = jwtService.generateToken(employee);
        List<String> roles = employee.getRoles().stream().map(Enum::name).sorted().toList();
        List<String> permissions = RolePermissions.permissionsFor(employee.getRoles()).stream()
                .map(Permission::name).sorted().toList();

        return EmployeeLoginResponse.of(
                token, jwtService.expirationSeconds(), EmployeeSummary.from(employee),
                roles, permissions, BranchSummary.from(employee.getBranch()));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getCurrentEmployee(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return EmployeeResponse.from(employee);
    }
}
