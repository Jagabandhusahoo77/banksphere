package com.banksphere.employee.service;

import com.banksphere.employee.dto.TransactionLookupResult;

import java.util.List;
import java.util.UUID;

/** Calls transaction-service's Customer 360 employee endpoint, forwarding the acting employee's own bearer token. See ADR-008. */
public interface TransactionLookupClient {

    List<TransactionLookupResult> recentTransactionsForAccount(UUID accountId, String bearerToken);
}
