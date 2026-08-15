package com.banksphere.employee.dto;

import jakarta.validation.constraints.NotBlank;

public record EmployeeLoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {
    /** Redacts the password so an accidental {@code log.info("{}", request)} never leaks it. */
    @Override
    public String toString() {
        return "EmployeeLoginRequest[username=%s, password=REDACTED]".formatted(username);
    }
}
