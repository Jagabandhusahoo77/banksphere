package com.banksphere.account.exception;

import java.util.UUID;

/**
 * Thrown when a transfer's source and destination accounts hold different
 * currencies. There is no FX/conversion capability anywhere in BankSphere,
 * so a currency mismatch is always rejected rather than silently converted.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(UUID sourceAccountId, String sourceCurrency,
                                      UUID destinationAccountId, String destinationCurrency) {
        super("Cannot transfer between accounts with different currencies: source %s (%s), destination %s (%s)"
                .formatted(sourceAccountId, sourceCurrency, destinationAccountId, destinationCurrency));
    }
}
