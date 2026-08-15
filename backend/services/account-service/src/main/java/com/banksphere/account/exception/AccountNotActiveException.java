package com.banksphere.account.exception;

import com.banksphere.account.entity.AccountStatus;

import java.util.UUID;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super("Account %s is not active (current status: %s)".formatted(accountId, status));
    }
}
