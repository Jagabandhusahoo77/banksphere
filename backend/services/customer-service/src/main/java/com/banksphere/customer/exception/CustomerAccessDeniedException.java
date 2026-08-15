package com.banksphere.customer.exception;

import java.util.UUID;

/** Thrown when the authenticated customer tries to access or modify a different customer's profile. */
public class CustomerAccessDeniedException extends RuntimeException {

    public CustomerAccessDeniedException(UUID requestedId) {
        super("Not authorized to access customer: " + requestedId);
    }
}
