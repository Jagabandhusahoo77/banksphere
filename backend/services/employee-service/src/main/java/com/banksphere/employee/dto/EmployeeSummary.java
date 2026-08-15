package com.banksphere.employee.dto;

import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;

import java.util.UUID;

/** Minimal, safe employee fields — used in {@link EmployeeLoginResponse}. Never includes passwordHash. */
public record EmployeeSummary(
        UUID id,
        String employeeNumber,
        String username,
        String firstName,
        String lastName,
        String email,
        EmployeeStatus status
) {
    public static EmployeeSummary from(Employee employee) {
        return new EmployeeSummary(
                employee.getId(), employee.getEmployeeNumber(), employee.getUsername(),
                employee.getFirstName(), employee.getLastName(), employee.getEmail(), employee.getStatus());
    }
}
