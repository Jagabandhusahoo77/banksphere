package com.banksphere.employee.dto;

import java.util.UUID;

/** Deserialization target for customer-service's {@code GET /api/v1/customers/employee-lookup/{id}} response. See {@link AccountLookupResult}. */
public record CustomerLookupResult(UUID id, String firstName, String lastName, String status) {
}
