package com.banksphere.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        String accountNumber,
        BigDecimal balance,
        String currency,
        Instant asOf
) {
}
