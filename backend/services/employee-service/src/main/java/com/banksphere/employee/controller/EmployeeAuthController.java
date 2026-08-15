package com.banksphere.employee.controller;

import com.banksphere.employee.dto.EmployeeLoginRequest;
import com.banksphere.employee.dto.EmployeeLoginResponse;
import com.banksphere.employee.dto.EmployeeResponse;
import com.banksphere.employee.security.CurrentUser;
import com.banksphere.employee.service.EmployeeAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/employees/auth/login} is the one public endpoint in this
 * service (see SecurityConfig). {@code /api/v1/employees/me} requires a
 * valid employee token but no specific permission — any authenticated
 * employee may view their own profile, exactly like customer-service's
 * {@code /api/v1/auth/me}.
 */
@RestController
@RequiredArgsConstructor
public class EmployeeAuthController {

    private final EmployeeAuthService employeeAuthService;

    @PostMapping("/api/v1/employees/auth/login")
    public ResponseEntity<EmployeeLoginResponse> login(@Valid @RequestBody EmployeeLoginRequest request) {
        return ResponseEntity.ok(employeeAuthService.login(request));
    }

    @GetMapping("/api/v1/employees/me")
    public ResponseEntity<EmployeeResponse> me(Authentication authentication) {
        return ResponseEntity.ok(employeeAuthService.getCurrentEmployee(CurrentUser.id(authentication)));
    }
}
