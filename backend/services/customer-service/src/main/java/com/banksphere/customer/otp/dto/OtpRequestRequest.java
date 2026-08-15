package com.banksphere.customer.otp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code identifier} matches either the customer's email or phone number
 * (tried in that order) — deliberately one field, not a
 * {@code mobileNumber}-only field, since this service's existing login
 * identifier is email (see {@code LoginRequest}) and OTP login should
 * work for a customer using either one they have handy. No {@code
 * customerId} field exists — see ADR-009's account-enumeration section
 * for why the identifier is looked up, never trusted as an id.
 */
public record OtpRequestRequest(
        @NotBlank(message = "identifier is required")
        String identifier
) {
}
