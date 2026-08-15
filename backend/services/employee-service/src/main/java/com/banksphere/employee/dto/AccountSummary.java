package com.banksphere.employee.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummary(
        UUID id,
        String accountNumber,
        String accountType,
        BigDecimal balance,
        String currency,
        String status
) {
    public static AccountSummary from(AccountLookupResult account) {
        return new AccountSummary(account.id(), account.accountNumber(), account.accountType(),
                account.balance(), account.currency(), account.status());
    }
}
