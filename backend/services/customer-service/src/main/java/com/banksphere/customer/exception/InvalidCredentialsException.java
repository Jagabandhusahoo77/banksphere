package com.banksphere.customer.exception;

/**
 * Thrown for any login failure — unknown email, wrong password, or a
 * disabled account. Deliberately a single exception type with a single
 * generic message ("Invalid email or password") for all three cases, so
 * the response never reveals which one occurred — see
 * docs/security/authentication.md's account-enumeration section.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
