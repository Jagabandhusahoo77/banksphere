package com.banksphere.employee.service;

import com.banksphere.employee.dto.CashDepositHistoryEntry;
import com.banksphere.employee.dto.CashDepositRequest;
import com.banksphere.employee.dto.CashDepositResponse;
import com.banksphere.employee.dto.CustomerSearchResponse;

import java.util.List;
import java.util.UUID;

/** The "Employee Operations API" — see docs/architecture/employee-operations.md and ADR-007. */
public interface OperationsService {

    /** Exactly one of {@code accountNumber}/{@code customerId} must be non-null. */
    CustomerSearchResponse customerSearch(String accountNumber, UUID customerId, String bearerToken);

    /** The acting employee's identity/branch come only from {@code actingEmployeeId} (their own verified JWT subject), never the request body. */
    CashDepositResponse cashDeposit(CashDepositRequest request, UUID actingEmployeeId, String bearerToken);

    /** The acting employee's own branch's most recent cash deposits. */
    List<CashDepositHistoryEntry> cashDepositHistory(UUID actingEmployeeId, String bearerToken);
}
