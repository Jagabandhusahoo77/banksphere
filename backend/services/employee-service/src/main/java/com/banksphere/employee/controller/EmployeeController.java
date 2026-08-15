package com.banksphere.employee.controller;

import com.banksphere.employee.dto.CreateEmployeeRequest;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.UpdateEmployeeStatusRequest;
import com.banksphere.employee.security.CurrentUser;
import com.banksphere.employee.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Employee provisioning/administration. There is no public employee
 * registration — every endpoint here requires {@code EMPLOYEE_MANAGE}
 * (held only by {@code ADMIN} in this phase's role/permission mapping, see
 * {@code security/RolePermissions}), enforced server-side via
 * {@code @PreAuthorize}, not merely by hiding these actions from a
 * non-admin's navigation menu — per this phase's own instruction and
 * ADR-006, Decision 4.
 *
 * <p>Unlike most controllers elsewhere in this codebase, these endpoints
 * legitimately operate on a resource that is NOT the caller's own — an
 * admin managing another employee's record is the entire point — so there
 * is no per-request "does this id belong to the caller" ownership check
 * here. What replaces it is the permission check itself: only a caller
 * whose own token carries {@code EMPLOYEE_MANAGE} reaches any of these
 * methods at all (see the {@code employeeCannotAccessAnotherEmployee...}
 * test in {@code EmployeeControllerTest} proving a non-admin, even one
 * requesting their own colleague's id, is rejected before ownership is
 * even a relevant question).
 */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request, Authentication authentication) {
        EmployeeResponse response = employeeService.createEmployee(request, CurrentUser.id(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<List<EmployeeResponse>> listEmployees() {
        return ResponseEntity.ok(employeeService.listEmployees());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getEmployee(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<EmployeeResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateEmployeeStatusRequest request, Authentication authentication) {
        EmployeeResponse response = employeeService.updateStatus(id, request, CurrentUser.id(authentication));
        return ResponseEntity.ok(response);
    }
}
