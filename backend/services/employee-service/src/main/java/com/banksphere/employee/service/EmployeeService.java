package com.banksphere.employee.service;

import com.banksphere.employee.dto.CreateEmployeeRequest;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.dto.UpdateEmployeeStatusRequest;

import java.util.List;
import java.util.UUID;

/**
 * Employee provisioning/administration. Every method here requires the
 * caller to hold {@code EMPLOYEE_MANAGE} — enforced via
 * {@code @PreAuthorize} on {@code EmployeeController}, not in this class —
 * this interface has no "acting employee" ownership concept the way
 * beneficiary-service's does, because these operations are administrative,
 * not self-service.
 */
public interface EmployeeService {

    EmployeeResponse createEmployee(CreateEmployeeRequest request, UUID actingEmployeeId);

    List<EmployeeResponse> listEmployees();

    EmployeeResponse getEmployee(UUID id);

    EmployeeResponse updateStatus(UUID id, UpdateEmployeeStatusRequest request, UUID actingEmployeeId);
}
