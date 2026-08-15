package com.banksphere.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Password policy (see docs/security/authentication.md): 8–72 characters
 * (72 is BCrypt's own input limit — see JwtProperties/PasswordEncoder
 * usage in AuthServiceImpl), at least one letter and one digit. Deliberately
 * not more elaborate than that — see the policy doc for why.
 */
public record RegisterRequest(

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^[+]?[0-9()\\-\\s]{7,20}$", message = "phone must be a valid phone number")
        String phone,

        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "address is required")
        @Size(max = 500, message = "address must be at most 500 characters")
        String address,

        @NotBlank(message = "password is required")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "password must be 8-72 characters and include at least one letter and one number")
        String password
) {
    /** Redacts the password so an accidental {@code log.info("{}", request)} never leaks it. */
    @Override
    public String toString() {
        return "RegisterRequest[firstName=%s, lastName=%s, email=%s, phone=%s, dateOfBirth=%s, address=%s, password=REDACTED]"
                .formatted(firstName, lastName, email, phone, dateOfBirth, address);
    }
}
