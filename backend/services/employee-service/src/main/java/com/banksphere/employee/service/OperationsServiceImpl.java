package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountLookupResult;
import com.banksphere.employee.dto.AccountSummary;
import com.banksphere.employee.dto.CashDepositHistoryEntry;
import com.banksphere.employee.dto.CashDepositRequest;
import com.banksphere.employee.dto.CashDepositResponse;
import com.banksphere.employee.dto.CustomerLookupResult;
import com.banksphere.employee.dto.CustomerSearchResponse;
import com.banksphere.employee.dto.EmployeeDepositResult;
import com.banksphere.employee.entity.CashDepositOperation;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.exception.DownstreamOperationException;
import com.banksphere.employee.exception.EmployeeNotFoundException;
import com.banksphere.employee.repository.CashDepositOperationRepository;
import com.banksphere.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationsServiceImpl implements OperationsService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HISTORY_LIMIT = 20;

    private final EmployeeRepository employeeRepository;
    private final CashDepositOperationRepository cashDepositOperationRepository;
    private final AccountOperationsClient accountOperationsClient;
    private final CustomerLookupClient customerLookupClient;
    private final EmployeeAuditLog auditLog;

    @Override
    @Transactional(readOnly = true)
    public CustomerSearchResponse customerSearch(String accountNumber, UUID customerId, String bearerToken) {
        boolean hasAccountNumber = accountNumber != null && !accountNumber.isBlank();
        boolean hasCustomerId = customerId != null;
        if (hasAccountNumber == hasCustomerId) {
            throw new IllegalArgumentException("Provide exactly one of accountNumber or customerId");
        }

        UUID resolvedCustomerId = hasCustomerId ? customerId
                : accountOperationsClient.lookupByAccountNumber(accountNumber, bearerToken).customerId();

        List<AccountLookupResult> accounts = accountOperationsClient.lookupByCustomerId(resolvedCustomerId, bearerToken);
        CustomerLookupResult customer = customerLookupClient.lookup(resolvedCustomerId, bearerToken);

        return new CustomerSearchResponse(
                resolvedCustomerId,
                customer.firstName() + " " + customer.lastName(),
                accounts.stream().map(AccountSummary::from).toList());
    }

    @Override
    @Transactional
    public CashDepositResponse cashDeposit(CashDepositRequest request, UUID actingEmployeeId, String bearerToken) {
        Employee employee = employeeRepository.findById(actingEmployeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(actingEmployeeId));
        String branchCode = employee.getBranch().getBranchCode();
        String branchId = employee.getBranch().getId().toString();

        auditLog.cashDepositStarted(actingEmployeeId.toString(), employee.getEmployeeNumber(), branchId,
                request.accountId().toString(), request.amount());

        String description = buildDescription(branchCode, request.description());

        EmployeeDepositResult result;
        try {
            result = accountOperationsClient.deposit(request.accountId(), request.amount(), description, bearerToken);
        } catch (DownstreamOperationException ex) {
            auditLog.cashDepositFailed(actingEmployeeId.toString(), employee.getEmployeeNumber(), branchId,
                    request.accountId().toString(), request.amount(), ex.getMessage());
            throw ex;
        }

        String operationReference = generateOperationReference();
        AccountLookupResult account = result.account();

        // Only successful deposits are persisted here — see
        // CashDepositOperation's own javadoc and ADR-007, Decision 9: a
        // failed attempt (where account-service rejected the operation
        // before ever confirming which account/customer it targeted) has
        // no verified identifiers safe to record in this table; the
        // structured audit log above is the complete record of the
        // attempt either way.
        CashDepositOperation operation = CashDepositOperation.builder()
                .operationReference(operationReference)
                .employeeId(actingEmployeeId)
                .employeeNumber(employee.getEmployeeNumber())
                .branchId(employee.getBranch().getId())
                .branchCode(branchCode)
                .customerId(account.customerId())
                .accountId(account.id())
                .accountNumber(account.accountNumber())
                .amount(request.amount())
                .currency(account.currency())
                .status("COMPLETED")
                .transactionReference(result.transactionReference())
                .build();
        cashDepositOperationRepository.save(operation);

        auditLog.cashDepositSucceeded(actingEmployeeId.toString(), employee.getEmployeeNumber(), branchId,
                request.accountId().toString(), request.amount(), operationReference);

        return new CashDepositResponse(
                operationReference, account.id(), account.accountNumber(), account.balance(), account.currency(),
                result.transactionReference(), "COMPLETED", employee.getEmployeeNumber(), branchCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashDepositHistoryEntry> cashDepositHistory(UUID actingEmployeeId, String bearerToken) {
        Employee employee = employeeRepository.findById(actingEmployeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(actingEmployeeId));

        List<CashDepositOperation> operations = cashDepositOperationRepository.findByBranchIdOrderByCreatedAtDesc(
                employee.getBranch().getId(), PageRequest.of(0, HISTORY_LIMIT));

        // Customer names are never stored in cash_deposit_operations (see
        // its own javadoc) — resolved live here, once per distinct
        // customer on the page rather than once per row, and tolerated to
        // fail per-customer (a display hiccup must never hide a real
        // operation record).
        Map<UUID, String> namesByCustomerId = new HashMap<>();
        return operations.stream()
                .map(operation -> {
                    String customerName = namesByCustomerId.computeIfAbsent(operation.getCustomerId(), id -> {
                        try {
                            CustomerLookupResult customer = customerLookupClient.lookup(id, bearerToken);
                            return customer.firstName() + " " + customer.lastName();
                        } catch (DownstreamOperationException ex) {
                            return null;
                        }
                    });
                    return CashDepositHistoryEntry.from(operation, customerName);
                })
                .toList();
    }

    private String buildDescription(String branchCode, String employeeNote) {
        String base = "CASH DEPOSIT - Branch " + branchCode;
        return (employeeNote == null || employeeNote.isBlank()) ? base : base + " (" + employeeNote.trim() + ")";
    }

    private String generateOperationReference() {
        String candidate;
        do {
            candidate = "CD-" + String.format("%010d", Math.abs(RANDOM.nextLong() % 10_000_000_000L));
        } while (cashDepositOperationRepository.existsByOperationReference(candidate));
        return candidate;
    }
}
