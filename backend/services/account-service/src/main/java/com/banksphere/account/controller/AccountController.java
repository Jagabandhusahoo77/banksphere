package com.banksphere.account.controller;

import com.banksphere.account.dto.AccountCreateRequest;
import com.banksphere.account.dto.AccountResponse;
import com.banksphere.account.dto.AmountRequest;
import com.banksphere.account.dto.BalanceResponse;
import com.banksphere.account.dto.EmployeeDepositResponse;
import com.banksphere.account.dto.ResolveRecipientRequest;
import com.banksphere.account.dto.ResolveRecipientResponse;
import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.dto.TransferResponse;
import com.banksphere.account.security.CurrentUser;
import com.banksphere.account.security.EmployeeCurrentUser;
import com.banksphere.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * PUBLIC: none — every endpoint requires authentication (see
 * SecurityConfig). Every account lookup/mutation on the caller's own
 * account additionally enforces ownership (see AccountServiceImpl) — the
 * account id in the URL is never trusted on its own. Two endpoints are
 * deliberate exceptions, because they operate on someone else's account
 * by design: {@code transfer}'s destination and {@code resolveRecipient}
 * — both identify that account by business identifiers (account number +
 * IFSC), never an internal id, and both return only the minimum
 * information needed (see ADR-005).
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        AccountResponse response = accountService.createAccount(
                request, CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccount(id, CurrentUser.id(authentication)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByCustomer(
            @PathVariable UUID customerId, Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccountsByCustomer(customerId, CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(accountService.getBalance(id, CurrentUser.id(authentication)));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(accountService.deposit(
                id, request, CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader)));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(accountService.withdraw(
                id, request, CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader)));
    }

    /**
     * Verifies a transfer recipient by account number + IFSC before the
     * caller commits to a transfer (Phase 8B) — matches real bank "verify
     * payee" UX. Deliberately the one endpoint in this controller that
     * doesn't derive or check the caller's own identity beyond "are they
     * authenticated at all" (enforced by SecurityConfig for every path
     * under {@code /api/v1/accounts/**} regardless of this method's
     * signature): it looks up someone *else's* account on purpose, and its
     * response contains no internal id, customerId, or balance for that
     * account — see ADR-005 for the full reasoning, including why this
     * isn't an account-enumeration vulnerability.
     */
    @PostMapping("/resolve-recipient")
    public ResponseEntity<ResolveRecipientResponse> resolveRecipient(@Valid @RequestBody ResolveRecipientRequest request) {
        return ResponseEntity.ok(accountService.resolveRecipient(request));
    }

    /**
     * Internal account-to-account transfer (Phase 7A). The authenticated
     * caller must own {@code sourceAccountId}; the destination (identified
     * by account number + IFSC, not an internal UUID — see ADR-005) may
     * belong to any customer. See AccountServiceImpl#transfer and ADR-004
     * for the atomicity/locking/ledger design.
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(accountService.transfer(
                request, CurrentUser.id(authentication), CurrentUser.bearerToken(authorizationHeader)));
    }

    // ---- Employee-only endpoints (Phase 9B) ----------------------------
    //
    // Reachable only by a request authenticated as an EmployeePrincipal
    // (see EmployeeJwtAuthenticationFilter) holding the stated permission
    // — a customer token can never carry ACCOUNT_VIEW/CASH_DEPOSIT, so
    // @PreAuthorize alone already excludes every customer-originated
    // request from these three methods. See ADR-007.

    /**
     * Full-detail account lookup by business identifier (account number),
     * for an employee who has been handed one by a customer in person —
     * deliberately more revealing than {@link #resolveRecipient}'s
     * minimal response, since the caller here is an authenticated,
     * permissioned employee performing a real operation, not a peer
     * customer previewing a transfer. See ADR-007.
     */
    @GetMapping("/employee-lookup")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public ResponseEntity<AccountResponse> employeeLookupByAccountNumber(@RequestParam String accountNumber) {
        return ResponseEntity.ok(accountService.employeeLookupByAccountNumber(accountNumber));
    }

    /** Every account for a given customer — the "select account" step once a customer has been found. */
    @GetMapping("/employee-lookup/customer/{customerId}")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public ResponseEntity<List<AccountResponse>> employeeLookupByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(accountService.employeeLookupByCustomerId(customerId));
    }

    /**
     * A branch employee crediting a customer's account with physical
     * cash. Requires {@code CASH_DEPOSIT}; the acting employee's identity
     * and branch come only from their own verified JWT
     * (EmployeeCurrentUser.identity), never from the request body — see
     * ADR-007.
     */
    @PostMapping("/{id}/employee-deposit")
    @PreAuthorize("hasAuthority('CASH_DEPOSIT')")
    public ResponseEntity<EmployeeDepositResponse> employeeDeposit(
            @PathVariable UUID id,
            @Valid @RequestBody AmountRequest request,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(accountService.employeeDeposit(
                id, request, EmployeeCurrentUser.identity(authentication), CurrentUser.bearerToken(authorizationHeader)));
    }
}
