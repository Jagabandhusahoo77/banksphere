package com.banksphere.account.exception;

/**
 * Thrown when a recipient's IFSC doesn't match BankSphere's own IFSC
 * ({@code AccountServiceImpl#BANKSPHERE_IFSC}). BankSphere has no
 * interbank rails, payment switch, or branch model yet (see ADR-005) —
 * this is the explicit, honest rejection for "that's a real-looking IFSC,
 * but not one this bank can route to," rather than silently misrouting or
 * treating it the same as "no such account" (see
 * {@link RecipientNotFoundException}).
 */
public class UnsupportedIfscException extends RuntimeException {

    public UnsupportedIfscException(String ifsc) {
        super("BankSphere doesn't support transfers to IFSC " + ifsc
                + " yet — only BankSphere's own accounts are supported for transfers.");
    }
}
