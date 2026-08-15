package com.banksphere.beneficiary.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal for an employee-originated request — set by
 * {@link EmployeeJwtAuthenticationFilter}. Deliberately a distinct type
 * from the plain {@code String} (customer UUID) principal {@link
 * JwtAuthenticationFilter} sets, so an employee token can never be
 * silently misread as a customer id — see {@link EmployeeCurrentUser}.
 * No {@code branchId}/{@code branchIfsc}: beneficiaries are not
 * branch-scoped (same reasoning as KYC — see ADR-008).
 */
public record EmployeePrincipal(UUID employeeId, String employeeNumber, List<String> roles, List<String> permissions) {
}
