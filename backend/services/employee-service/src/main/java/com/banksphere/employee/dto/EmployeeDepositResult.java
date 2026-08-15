package com.banksphere.employee.dto;

/** Deserialization target for account-service's {@code POST .../employee-deposit} response. See {@link AccountLookupResult}. */
public record EmployeeDepositResult(AccountLookupResult account, String transactionReference) {
}
