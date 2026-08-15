package com.banksphere.employee.exception;

/**
 * Thrown for any login failure — unknown username, wrong password, or a
 * non-ACTIVE (INACTIVE/LOCKED) employee. Deliberately a single exception
 * type with a single generic message ("Invalid username or password") for
 * all cases, so the response never reveals which one occurred, or even
 * whether the username exists at all — same account-enumeration defense
 * customer-service's login uses, applied here even though employee
 * registration is admin-only (see docs/security/authentication.md and
 * ADR-006).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
