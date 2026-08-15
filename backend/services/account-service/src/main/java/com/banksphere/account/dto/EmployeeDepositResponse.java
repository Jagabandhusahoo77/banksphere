package com.banksphere.account.dto;

/**
 * {@code transactionReference} is the REAL {@code TXN-...} reference
 * transaction-service generated, when ledger recording succeeded — {@code
 * null} if it didn't (best-effort, per ADR-001/ADR-007; the deposit
 * itself is never rolled back or reported as failed just because this
 * one piece of it failed). Never a fabricated reference — see ADR-007,
 * Decision 5.
 */
public record EmployeeDepositResponse(AccountResponse account, String transactionReference) {
}
