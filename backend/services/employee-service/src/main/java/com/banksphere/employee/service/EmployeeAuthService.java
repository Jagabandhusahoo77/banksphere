package com.banksphere.employee.service;

import com.banksphere.employee.dto.EmployeeLoginRequest;
import com.banksphere.employee.dto.EmployeeLoginResponse;
import com.banksphere.employee.dto.EmployeeResponse;

import java.util.UUID;

public interface EmployeeAuthService {

    EmployeeLoginResponse login(EmployeeLoginRequest request);

    EmployeeResponse getCurrentEmployee(UUID employeeId);
}
