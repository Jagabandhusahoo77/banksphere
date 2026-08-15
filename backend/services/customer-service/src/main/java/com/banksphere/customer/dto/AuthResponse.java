package com.banksphere.customer.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CustomerSummary customer
) {
    public static AuthResponse of(String accessToken, long expiresIn, CustomerSummary customer) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, customer);
    }
}
