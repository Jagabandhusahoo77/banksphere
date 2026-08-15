package com.banksphere.employee.dto;

import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Permission;
import com.banksphere.employee.security.RolePermissions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full employee profile shape — {@code GET /me} and the admin
 * employee-management endpoints both return this. {@code passwordHash} is
 * structurally absent: there is no field for it here to redact, per
 * CLAUDE.md's password-DTO rule.
 */
public record EmployeeResponse(
        UUID id,
        String employeeNumber,
        String username,
        String firstName,
        String lastName,
        String email,
        List<String> roles,
        List<String> permissions,
        BranchSummary branch,
        EmployeeStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static EmployeeResponse from(Employee employee) {
        List<String> roleNames = employee.getRoles().stream().map(Enum::name).sorted().toList();
        List<String> permissionNames = RolePermissions.permissionsFor(employee.getRoles()).stream()
                .map(Permission::name).sorted().toList();
        return new EmployeeResponse(
                employee.getId(), employee.getEmployeeNumber(), employee.getUsername(),
                employee.getFirstName(), employee.getLastName(), employee.getEmail(),
                roleNames, permissionNames, BranchSummary.from(employee.getBranch()),
                employee.getStatus(), employee.getCreatedAt(), employee.getUpdatedAt());
    }
}
