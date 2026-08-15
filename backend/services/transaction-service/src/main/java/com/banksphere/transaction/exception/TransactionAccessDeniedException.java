package com.banksphere.transaction.exception;

import java.util.UUID;

/** Thrown when the authenticated caller doesn't own the account a transaction/transaction-history request targets. */
public class TransactionAccessDeniedException extends RuntimeException {

    public TransactionAccessDeniedException(UUID accountId) {
        super("Not authorized to access transactions for account: " + accountId);
    }
}
