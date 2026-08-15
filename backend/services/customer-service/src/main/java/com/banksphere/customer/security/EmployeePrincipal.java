package com.banksphere.customer.security;

import java.util.List;
import java.util.UUID;

/** See account-service's identically-purposed record and ADR-007. */
public record EmployeePrincipal(UUID employeeId, String employeeNumber, List<String> permissions) {
}
