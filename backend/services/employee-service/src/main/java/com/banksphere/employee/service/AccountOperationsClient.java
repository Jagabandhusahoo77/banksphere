package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountLookupResult;
import com.banksphere.employee.dto.EmployeeDepositResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Calls account-service's employee-only endpoints, forwarding the acting
 * employee's own bearer token so account-service can independently
 * verify it (see account-service's {@code EmployeeJwtAuthenticationFilter}
 * and ADR-007) — never a shared internal secret, never re-asserted
 * identity fields. Unlike {@code TransactionClient}
 * (best-effort, never rethrows), these calls that FEED the operation
 * (lookup, and the deposit mutation itself) DO propagate failures — a
 * failed lookup or a rejected deposit must be reported truthfully, not
 * swallowed.
 */
public interface AccountOperationsClient {

    AccountLookupResult lookupByAccountNumber(String accountNumber, String bearerToken);

    List<AccountLookupResult> lookupByCustomerId(UUID customerId, String bearerToken);

    EmployeeDepositResult deposit(UUID accountId, BigDecimal amount, String description, String bearerToken);
}
