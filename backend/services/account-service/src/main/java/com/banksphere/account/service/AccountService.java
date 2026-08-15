package com.banksphere.account.service;

import com.banksphere.account.dto.AccountCreateRequest;
import com.banksphere.account.dto.AccountResponse;
import com.banksphere.account.dto.AmountRequest;
import com.banksphere.account.dto.BalanceResponse;
import com.banksphere.account.dto.EmployeeDepositResponse;
import com.banksphere.account.dto.ResolveRecipientRequest;
import com.banksphere.account.dto.ResolveRecipientResponse;
import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.dto.TransferResponse;
import com.banksphere.account.security.EmployeePrincipal;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    /** The new account is always owned by {@code ownerCustomerId} (the authenticated caller) — never client-supplied. */
    AccountResponse createAccount(AccountCreateRequest request, UUID ownerCustomerId, String bearerToken);

    /** Throws AccountAccessDeniedException if the account isn't owned by {@code requestingCustomerId}. */
    AccountResponse getAccount(UUID id, UUID requestingCustomerId);

    /** Throws AccountAccessDeniedException if {@code customerId} != {@code requestingCustomerId}. */
    List<AccountResponse> getAccountsByCustomer(UUID customerId, UUID requestingCustomerId);

    /** Throws AccountAccessDeniedException if the account isn't owned by {@code requestingCustomerId}. */
    BalanceResponse getBalance(UUID id, UUID requestingCustomerId);

    AccountResponse deposit(UUID id, AmountRequest request, UUID requestingCustomerId, String bearerToken);

    AccountResponse withdraw(UUID id, AmountRequest request, UUID requestingCustomerId, String bearerToken);

    /**
     * Moves money from {@code request.sourceAccountId()} to the account
     * identified by {@code request.destinationAccountNumber()} +
     * {@code request.destinationIfsc()}, atomically. Throws
     * AccountAccessDeniedException if the source account isn't owned by
     * {@code requestingCustomerId} — the destination account does not
     * need to be owned by the caller. See AccountServiceImpl#transfer's
     * javadoc, ADR-004 (atomicity/locking/ledger) and ADR-005 (recipient
     * resolution by account number + IFSC, not an internal UUID).
     */
    TransferResponse transfer(TransferRequest request, UUID requestingCustomerId, String bearerToken);

    /**
     * Verifies a transfer recipient by business identifiers (account
     * number + IFSC) before the caller commits to a transfer — matches
     * real bank "verify payee" UX. Does not require or use the caller's
     * own identity (it looks up someone else's account, not the
     * caller's), and its response deliberately contains no internal
     * account id, customerId, or balance. See ADR-005.
     */
    ResolveRecipientResponse resolveRecipient(ResolveRecipientRequest request);

    /**
     * Phase 9B — employee-only account lookup by business identifier (an
     * employee has an account NUMBER a customer gave them, not an internal
     * id). Deliberately a full {@link AccountResponse} (including
     * customerId/balance), unlike {@link #resolveRecipient}'s minimal
     * response — that endpoint is public-ish and minimizes disclosure by
     * design (ADR-005); this one is reachable only by an authenticated
     * employee holding {@code ACCOUNT_VIEW}, performing a real banking
     * operation that needs the fuller picture. See ADR-007.
     */
    AccountResponse employeeLookupByAccountNumber(String accountNumber);

    /** Phase 9B — every account for a given customer, for an employee holding {@code ACCOUNT_VIEW}. No self-ownership check (there is no "self" for an employee). */
    List<AccountResponse> employeeLookupByCustomerId(UUID customerId);

    /**
     * Phase 9B — a branch employee crediting a customer's account with
     * physical cash. Unlike {@link #deposit}, there is no owning-customer
     * check (the whole point is crediting someone else's account); instead
     * {@code actingEmployee}'s branch is checked against the account's own
     * IFSC unless the employee holds a broader-scope role — see
     * {@link EmployeePrincipal#hasBroadBranchScope()} and ADR-007,
     * Decision 6. Reuses the exact same {@code credit()}/{@code
     * requireActive()} helpers and best-effort ledger-recording pattern
     * {@link #deposit} already uses — this is not a parallel money-movement
     * implementation, it's the same one with a different authorization
     * gate in front of it.
     */
    EmployeeDepositResponse employeeDeposit(
            UUID accountId, AmountRequest request, EmployeePrincipal actingEmployee, String bearerToken);
}
