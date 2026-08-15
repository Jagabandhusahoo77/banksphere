package com.banksphere.kyc.exception;

/**
 * Thrown by {@code CurrentUser.id} when an otherwise-validly-authenticated
 * request reaches a customer-only KYC endpoint (e.g.
 * {@code /api/v1/kyc/applications/**}) carrying an {@code EmployeePrincipal}
 * rather than a customer identity — e.g. an employee token presented where
 * a customer token is required. Mapped to {@code 403} by
 * {@code GlobalExceptionHandler}: the token is valid and we know exactly
 * who it belongs to, we just don't allow that principal type here (see
 * CLAUDE.md's 401-vs-403 rule). Employee-only endpoints under
 * {@code /api/v1/kyc/employee/**} are protected the other direction by
 * {@code @PreAuthorize} — a customer token never carries a {@code KYC_*}
 * authority, so it is rejected before ever reaching
 * {@code EmployeeCurrentUser.identity}.
 */
public class WrongPrincipalTypeException extends RuntimeException {

    public WrongPrincipalTypeException(String message) {
        super(message);
    }
}
