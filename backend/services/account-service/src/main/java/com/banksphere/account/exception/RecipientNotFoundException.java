package com.banksphere.account.exception;

/**
 * Thrown when an account number that has already passed IFSC validation
 * (i.e. it claims to be a BankSphere account) doesn't match any real
 * account. Deliberately distinct from {@link AccountNotFoundException},
 * which is keyed by internal UUID (an operation on a resource the caller
 * is expected to already have looked up) — this one is keyed by the
 * business identifier a customer typed in by hand while trying to find
 * someone else's account, exactly the "recipient not found" case a real
 * bank's payee-verification flow surfaces.
 */
public class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(String accountNumber) {
        super("No BankSphere account found for account number " + accountNumber);
    }
}
