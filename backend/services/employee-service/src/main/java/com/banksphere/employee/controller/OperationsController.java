package com.banksphere.employee.controller;

import com.banksphere.employee.dto.CashDepositHistoryEntry;
import com.banksphere.employee.dto.CashDepositRequest;
import com.banksphere.employee.dto.CashDepositResponse;
import com.banksphere.employee.dto.CustomerSearchResponse;
import com.banksphere.employee.security.CurrentUser;
import com.banksphere.employee.service.OperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The "Employee Operations API" — the single channel employee-portal
 * talks to for any real banking operation; this controller orchestrates
 * calls to account-service/customer-service on the caller's behalf,
 * forwarding their own verified bearer token to each (see
 * OperationsServiceImpl, AccountOperationsClient, CustomerLookupClient).
 * See docs/architecture/employee-operations.md and ADR-007.
 *
 * <p>{@code CASH_DEPOSIT} gates the deposit itself; the search/lookup
 * endpoint is gated by {@code ACCOUNT_VIEW}/{@code CUSTOMER_VIEW} instead
 * (browsing isn't money-moving). In the existing Phase 9A mapping every
 * one of the 7 roles holds both of those — this endpoint's real
 * authorization boundary is "is this an employee at all," which is
 * exactly appropriate for a read-only lookup every operational role needs
 * in its own domain, not just cash deposit's.
 */
@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService operationsService;

    @GetMapping("/customer-search")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW') and hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerSearchResponse> customerSearch(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) UUID customerId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(operationsService.customerSearch(
                accountNumber, customerId, CurrentUser.bearerToken(authorizationHeader)));
    }

    @PostMapping("/cash-deposits")
    @PreAuthorize("hasAuthority('CASH_DEPOSIT')")
    public ResponseEntity<CashDepositResponse> cashDeposit(
            @Valid @RequestBody CashDepositRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        CashDepositResponse response = operationsService.cashDeposit(
                request, CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader));
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/cash-deposits/history")
    @PreAuthorize("hasAuthority('CASH_DEPOSIT')")
    public ResponseEntity<List<CashDepositHistoryEntry>> cashDepositHistory(
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(operationsService.cashDepositHistory(
                CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader)));
    }
}
