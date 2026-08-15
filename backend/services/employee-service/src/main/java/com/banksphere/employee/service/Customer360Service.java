package com.banksphere.employee.service;

import com.banksphere.employee.dto.Customer360Response;

import java.util.Set;
import java.util.UUID;

/**
 * The Customer 360 aggregation — see docs/architecture/customer-360-and-kyc.md
 * and ADR-008. {@code callerPermissions} drives section-level graceful
 * degradation: only sections the caller's own authorities actually
 * include get populated with real data from the corresponding downstream
 * service; every other section comes back explicitly unavailable, never
 * fabricated and never silently omitted.
 */
public interface Customer360Service {

    Customer360Response getCustomer360(UUID customerId, Set<String> callerPermissions, String bearerToken);
}
