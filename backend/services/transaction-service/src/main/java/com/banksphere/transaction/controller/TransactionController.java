package com.banksphere.transaction.controller;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.security.CurrentUser;
import com.banksphere.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * PUBLIC: none — every endpoint requires a valid JWT (see SecurityConfig).
 * {@code POST /} additionally requires no further check beyond that (see
 * TransactionService's javadoc for why); the two GET endpoints
 * additionally verify — via a call to account-service, forwarding the
 * caller's own token — that the authenticated caller owns the account
 * being queried.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Internal endpoint used by account-service (and future payment-service)
     * to append a completed transaction to the ledger. Requires a valid
     * JWT but does not itself re-verify account ownership — see
     * TransactionService.createTransaction's javadoc.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(transactionService.getTransaction(id, CurrentUser.bearerToken(authorizationHeader)));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactionsByAccount(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccount(
                accountId, pageable, CurrentUser.bearerToken(authorizationHeader)));
    }

    /**
     * Phase 9C — Customer 360's transactions section. Reachable only by
     * an employee token holding {@code TRANSACTION_VIEW} (a customer
     * token can never carry that authority). No ownership check — see
     * TransactionService.getTransactionsByAccountForEmployee's javadoc
     * and ADR-008.
     */
    @GetMapping("/employee/account/{accountId}")
    @PreAuthorize("hasAuthority('TRANSACTION_VIEW')")
    public ResponseEntity<PageResponse<TransactionResponse>> getTransactionsByAccountForEmployee(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionsByAccountForEmployee(accountId, pageable));
    }
}
