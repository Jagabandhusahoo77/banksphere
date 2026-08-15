package com.banksphere.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
    /** Redacts the password so an accidental {@code log.info("{}", request)} never leaks it. */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=REDACTED]".formatted(email);
    }
}
