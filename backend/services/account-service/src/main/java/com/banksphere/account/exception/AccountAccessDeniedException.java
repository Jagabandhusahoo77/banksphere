package com.banksphere.account.exception;

import java.util.UUID;

/** Thrown when the authenticated customer tries to access an account (or another customer's account list) they don't own. */
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(UUID requestedResourceId) {
        super("Not authorized to access this resource: " + requestedResourceId);
    }
}
