package com.banksphere.employee.dto;

import com.banksphere.employee.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * There is no public employee self-registration — every employee is
 * provisioned by an already-authenticated administrator holding
 * {@code EMPLOYEE_MANAGE} (see EmployeeController, ADR-006 Decision 6).
 * Password policy matches customer-service's: 8-72 characters (72 is
 * BCrypt's own input limit), at least one letter and one digit.
 */
public record CreateEmployeeRequest(
        @NotBlank(message = "employeeNumber is required")
        @Size(max = 20, message = "employeeNumber must be at most 20 characters")
        String employeeNumber,

        @NotBlank(message = "username is required")
        @Size(max = 50, message = "username must be at most 50 characters")
        String username,

        @NotBlank(message = "password is required")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "password must be 8-72 characters and include at least one letter and one number")
        String password,

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotNull(message = "branchId is required")
        UUID branchId,

        @NotEmpty(message = "at least one role is required")
        Set<Role> roles
) {
    /** Redacts the password so an accidental {@code log.info("{}", request)} never leaks it. */
    @Override
    public String toString() {
        return "CreateEmployeeRequest[employeeNumber=%s, username=%s, password=REDACTED, firstName=%s, lastName=%s, email=%s, branchId=%s, roles=%s]"
                .formatted(employeeNumber, username, firstName, lastName, email, branchId, roles);
    }
}
