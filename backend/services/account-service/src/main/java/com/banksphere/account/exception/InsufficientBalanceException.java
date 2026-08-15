package com.banksphere.account.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(UUID accountId, BigDecimal balance, BigDecimal requestedAmount) {
        super("Account %s has insufficient balance: available=%s, requested=%s"
                .formatted(accountId, balance, requestedAmount));
    }
}
