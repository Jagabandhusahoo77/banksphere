package com.banksphere.customer.dto;

import com.banksphere.customer.entity.Customer;
import com.banksphere.customer.entity.CustomerStatus;

import java.util.UUID;

/**
 * Deliberately slimmer than {@link CustomerResponse} — an employee doing a
 * cash deposit needs to confirm "which customer is this" (a name), not
 * see phone/dateOfBirth/address/email, none of which the operation needs.
 * See ADR-007 and this phase's own "do not expose sensitive information
 * unnecessarily" instruction.
 */
public record CustomerLookupResponse(UUID id, String firstName, String lastName, CustomerStatus status) {

    public static CustomerLookupResponse from(Customer customer) {
        return new CustomerLookupResponse(customer.getId(), customer.getFirstName(), customer.getLastName(), customer.getStatus());
    }
}
