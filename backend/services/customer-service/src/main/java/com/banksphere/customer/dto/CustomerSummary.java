package com.banksphere.customer.dto;

import com.banksphere.customer.entity.Customer;

import java.util.UUID;

/** Minimal, safe customer fields — used only in {@link AuthResponse}. Never includes passwordHash. */
public record CustomerSummary(UUID id, String firstName, String lastName, String email) {

    public static CustomerSummary from(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getFirstName(), customer.getLastName(), customer.getEmail());
    }
}
