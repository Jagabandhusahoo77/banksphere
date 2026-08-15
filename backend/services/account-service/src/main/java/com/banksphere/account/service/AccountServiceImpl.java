package com.banksphere.account.service;

import com.banksphere.account.dto.AccountCreateRequest;
import com.banksphere.account.dto.AccountResponse;
import com.banksphere.account.dto.AmountRequest;
import com.banksphere.account.dto.BalanceResponse;
import com.banksphere.account.dto.EmployeeDepositResponse;
import com.banksphere.account.dto.ResolveRecipientRequest;
import com.banksphere.account.dto.ResolveRecipientResponse;
import com.banksphere.account.dto.TransactionRecordRequest;
import com.banksphere.account.dto.TransactionRecordResult;
import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.dto.TransferResponse;
import com.banksphere.account.entity.Account;
import com.banksphere.account.entity.AccountStatus;
import com.banksphere.account.exception.AccountAccessDeniedException;
import com.banksphere.account.exception.AccountNotActiveException;
import com.banksphere.account.exception.AccountNotFoundException;
import com.banksphere.account.exception.BranchScopeViolationException;
import com.banksphere.account.exception.CurrencyMismatchException;
import com.banksphere.account.exception.InsufficientBalanceException;
import com.banksphere.account.exception.RecipientNotFoundException;
import com.banksphere.account.exception.StepUpRequiredException;
import com.banksphere.account.exception.UnsupportedIfscException;
import com.banksphere.account.repository.AccountRepository;
import com.banksphere.account.security.EmployeePrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * BankSphere is a single fictional bank with no branch model — every
     * account gets this same demo IFSC, set here (server-side) and never
     * accepted from a request. If BankSphere ever adds real branches, this
     * becomes a lookup instead of a constant; nothing about the DTO/entity
     * shape needs to change for that later.
     */
    public static final String BANKSPHERE_IFSC = "BANK0000001";

    private final AccountRepository accountRepository;
    private final TransactionClient transactionClient;
    private final TransferIdempotencyService idempotencyService;
    private final StepUpPolicy stepUpPolicy;
    private final StepUpVerificationClient stepUpVerificationClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request, UUID ownerCustomerId, String bearerToken) {
        BigDecimal initialDeposit = request.initialDeposit() != null ? request.initialDeposit() : BigDecimal.ZERO;

        Account account = Account.builder()
                .customerId(ownerCustomerId)
                .accountNumber(generateUniqueAccountNumber())
                .ifsc(BANKSPHERE_IFSC)
                .accountType(request.accountType())
                .currency(request.currency())
                .balance(initialDeposit)
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);

        if (initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
            transactionClient.recordTransaction(new TransactionRecordRequest(
                    saved.getId(), "DEPOSIT", initialDeposit, saved.getCurrency(), "Initial deposit"), bearerToken);
        }

        return AccountResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id, UUID requestingCustomerId) {
        Account account = findAccountOrThrow(id);
        requireOwnership(account, requestingCustomerId);
        return AccountResponse.from(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByCustomer(UUID customerId, UUID requestingCustomerId) {
        if (!customerId.equals(requestingCustomerId)) {
            throw new AccountAccessDeniedException(customerId);
        }
        return accountRepository.findByCustomerId(customerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID id, UUID requestingCustomerId) {
        Account account = findAccountOrThrow(id);
        requireOwnership(account, requestingCustomerId);
        return new BalanceResponse(account.getId(), account.getAccountNumber(), account.getBalance(),
                account.getCurrency(), Instant.now());
    }

    @Override
    @Transactional
    public AccountResponse deposit(UUID id, AmountRequest request, UUID requestingCustomerId, String bearerToken) {
        Account account = findAccountOrThrow(id);
        requireOwnership(account, requestingCustomerId);
        requireActive(account);

        credit(account, request.amount());
        Account saved = accountRepository.save(account);

        transactionClient.recordTransaction(new TransactionRecordRequest(
                saved.getId(), "DEPOSIT", request.amount(), saved.getCurrency(), request.description()), bearerToken);

        return AccountResponse.from(saved);
    }

    @Override
    @Transactional
    public AccountResponse withdraw(UUID id, AmountRequest request, UUID requestingCustomerId, String bearerToken) {
        Account account = findAccountOrThrow(id);
        requireOwnership(account, requestingCustomerId);
        requireActive(account);
        requireSufficientBalance(account, request.amount());

        debit(account, request.amount());
        Account saved = accountRepository.save(account);

        transactionClient.recordTransaction(new TransactionRecordRequest(
                saved.getId(), "WITHDRAWAL", request.amount(), saved.getCurrency(), request.description()), bearerToken);

        return AccountResponse.from(saved);
    }

    /**
     * Internal account-to-account transfer. See ADR-004 for the full
     * atomicity/locking/ledger design and ADR-005 for why the destination
     * is identified by account number + IFSC here, not an internal UUID
     * (Phase 8B); recipient resolution happens first, strictly before any
     * balance mutation, via the same {@link #resolveDestinationAccount}
     * helper {@link #resolveRecipient} uses for its preview — there is
     * exactly one place this logic lives.
     *
     * <p><b>Phase 9D</b> — this method itself is now just an idempotency
     * wrapper around {@link #executeTransfer}, which does the actual
     * validation/step-up/mutation work (see its own javadoc for the
     * account-ordering/locking/ledger design that used to be documented
     * here). {@code idempotencyService}'s methods each run in their own
     * {@code REQUIRES_NEW} transaction (see its own javadoc) specifically
     * so this wrapper's own {@code @Transactional} boundary — the one
     * that protects the actual balance mutation — can roll back on
     * failure without also erasing the record that the attempt happened
     * at all. A client that omits {@code idempotencyKey} entirely gets no
     * idempotency protection (this call becomes a no-op passthrough) —
     * every real client should supply one, but it is not mandatory, to
     * avoid breaking any existing caller (e.g. the real-Postgres
     * integration tests written before this phase).
     */
    @Override
    @Transactional
    public TransferResponse transfer(TransferRequest request, UUID requestingCustomerId, String bearerToken) {
        boolean hasIdempotencyKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank();
        UUID idempotencyRecordId = null;

        if (hasIdempotencyKey) {
            TransferIdempotencyResult check = idempotencyService.checkOrCreate(requestingCustomerId, request.idempotencyKey());
            if (check.alreadyCompleted()) {
                return deserializeSnapshot(check.responseSnapshot());
            }
            idempotencyRecordId = check.recordId();
        }

        try {
            TransferResponse response = executeTransfer(request, requestingCustomerId, bearerToken);
            if (idempotencyRecordId != null) {
                idempotencyService.markCompleted(idempotencyRecordId, response.transferId(), serializeSnapshot(response));
            }
            return response;
        } catch (RuntimeException ex) {
            if (idempotencyRecordId != null) {
                idempotencyService.markFailed(idempotencyRecordId);
            }
            throw ex;
        }
    }

    /**
     * The actual transfer — ownership/validity/balance checks, the
     * step-up gate (Phase 9D), and the balance mutation itself. See
     * {@link #transfer} for the idempotency wrapper around this method.
     *
     * <p><b>Step-up gate:</b> {@link StepUpPolicy#requiresStepUpForTransfer}
     * runs only after every other validation has already passed — no
     * point asking a customer to complete an OTP for a transfer that
     * would fail anyway (insufficient balance, inactive account, etc.).
     * If required, {@code request.stepUpChallengeId()} must be present
     * and customer-service must confirm it — see {@link
     * StepUpVerificationClient}, which forwards the caller's own bearer
     * token and never treats an unreachable customer-service as
     * "satisfied." This happens strictly BEFORE any balance mutation.
     *
     * <p><b>Deterministic account ordering (deadlock avoidance):</b> the
     * two accounts are always mutated and flushed in a fixed order — by
     * ascending id, independent of which one is source vs destination —
     * so that a concurrent transfer running in the opposite direction
     * (B→A while this one runs A→B) always attempts its first row-level
     * write against the same account, never the opposite one. That
     * removes the classic "opposite lock order" deadlock shape; it does
     * not replace optimistic locking, which remains the mechanism that
     * actually detects and rejects a lost update (see {@code @Version} on
     * {@link Account}). {@code saveAndFlush} (not {@code save}) is used
     * deliberately, so the UPDATE for the first account is actually sent
     * to Postgres — and its row lock acquired — before the second
     * account's UPDATE is issued, rather than relying on Hibernate's own
     * (unspecified) automatic flush ordering.
     *
     * <p><b>Ledger recording is best-effort</b>, exactly like deposit/
     * withdraw (ADR-001): it happens only after both balance writes have
     * already succeeded in this transaction, and {@link TransactionClient}
     * never rethrows, so a transaction-service outage cannot roll back an
     * already-committed transfer.
     */
    private TransferResponse executeTransfer(TransferRequest request, UUID requestingCustomerId, String bearerToken) {
        Account source = findAccountOrThrow(request.sourceAccountId());
        requireOwnership(source, requestingCustomerId);
        requireActive(source);

        Account destination = resolveDestinationAccount(request.destinationAccountNumber(), request.destinationIfsc());

        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException("Source and destination account must be different");
        }
        requireActive(destination);

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new CurrencyMismatchException(
                    source.getId(), source.getCurrency(), destination.getId(), destination.getCurrency());
        }

        BigDecimal amount = request.amount();
        requireSufficientBalance(source, amount);

        if (stepUpPolicy.requiresStepUpForTransfer(amount)) {
            if (request.stepUpChallengeId() == null) {
                throw new StepUpRequiredException();
            }
            stepUpVerificationClient.confirmTransferStepUp(request.stepUpChallengeId(), source.getId(),
                    request.destinationAccountNumber(), request.destinationIfsc(), amount, source.getCurrency(), bearerToken);
        }

        // Deterministic ascending-id flush order, computed now that both
        // accounts' real ids are known — see the class javadoc above.
        boolean sourceIsFirst = source.getId().compareTo(destination.getId()) < 0;
        Account first = sourceIsFirst ? source : destination;
        Account second = sourceIsFirst ? destination : source;

        debit(source, amount);
        credit(destination, amount);

        accountRepository.saveAndFlush(first);
        accountRepository.saveAndFlush(second);

        UUID transferId = UUID.randomUUID();
        Instant now = Instant.now();
        String currency = source.getCurrency();

        // Human-readable account numbers in the ledger description (not
        // the old Phase 7A default of the destination's internal UUID) —
        // more useful to a customer reading their own transaction history,
        // and still matches the "Transfer to account "/"Transfer from
        // account " prefix the frontend's direction-inference heuristic
        // looks for (see utils/transactionDirection.ts).
        transactionClient.recordTransaction(new TransactionRecordRequest(
                source.getId(), "TRANSFER", amount, currency,
                transferLegDescription(request.description(), "Transfer to account " + destination.getAccountNumber())), bearerToken);
        transactionClient.recordTransaction(new TransactionRecordRequest(
                destination.getId(), "TRANSFER", amount, currency,
                transferLegDescription(request.description(), "Transfer from account " + source.getAccountNumber())), bearerToken);

        return new TransferResponse(transferId, source.getId(), destination.getAccountNumber(), destination.getIfsc(),
                amount, currency, "COMPLETED", now);
    }

    private String serializeSnapshot(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            // Never let a serialization hiccup fail an already-successful
            // transfer — the customer's money has already moved
            // correctly; a retry with the same idempotency key simply
            // won't have a cached snapshot to replay (falls through to
            // IdempotencyConflictException on next attempt, which is
            // safe, if slightly less convenient than a perfect replay).
            log.error("Failed to serialize transfer idempotency snapshot for transfer {}: {}", response.transferId(), ex.getMessage());
            return null;
        }
    }

    private TransferResponse deserializeSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, TransferResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not replay cached transfer result", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResolveRecipientResponse resolveRecipient(ResolveRecipientRequest request) {
        Account account = resolveDestinationAccount(request.accountNumber(), request.ifsc());
        requireActive(account);
        return ResolveRecipientResponse.of(account);
    }

    /**
     * Shared by {@link #resolveRecipient} (preview, before the customer
     * commits) and {@link #transfer} (the actual atomic mutation) so the
     * two can never drift — "this recipient is valid" and "this recipient
     * is who actually gets credited" are always the same check. IFSC is
     * validated first (cheap, no DB access) since a non-BankSphere IFSC
     * is rejected outright regardless of whether the account number would
     * otherwise resolve to something.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountResponse employeeLookupByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RecipientNotFoundException(accountNumber));
        return AccountResponse.from(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> employeeLookupByCustomerId(UUID customerId) {
        return accountRepository.findByCustomerId(customerId).stream().map(AccountResponse::from).toList();
    }

    /**
     * See ADR-007, Decision 6 for the branch-scoping design in full. In
     * short: {@code Account} has no branch field of its own — it never
     * needed one until this phase, and inventing one now would duplicate
     * information the account's own {@code ifsc} already carries (one
     * IFSC == one branch in this codebase's model, exactly as real IFSCs
     * work). A TELLER may only credit an account whose {@code ifsc}
     * matches their own branch's; BRANCH_MANAGER/ADMIN are not restricted
     * this way (a documented broader-scope decision, not an oversight).
     */
    @Override
    @Transactional
    public EmployeeDepositResponse employeeDeposit(
            UUID accountId, AmountRequest request, EmployeePrincipal actingEmployee, String bearerToken) {
        Account account = findAccountOrThrow(accountId);
        requireActive(account);
        requireBranchScope(account, actingEmployee);

        credit(account, request.amount());
        Account saved = accountRepository.save(account);

        Optional<TransactionRecordResult> recorded = transactionClient.recordTransaction(new TransactionRecordRequest(
                saved.getId(), "DEPOSIT", request.amount(), saved.getCurrency(), request.description()), bearerToken);

        return new EmployeeDepositResponse(
                AccountResponse.from(saved),
                recorded.map(TransactionRecordResult::transactionReference).orElse(null));
    }

    private void requireBranchScope(Account account, EmployeePrincipal actingEmployee) {
        if (actingEmployee.hasBroadBranchScope()) {
            return;
        }
        if (!account.getIfsc().equalsIgnoreCase(actingEmployee.branchIfsc())) {
            throw new BranchScopeViolationException(account.getId(), account.getIfsc(), actingEmployee.branchIfsc());
        }
    }

    private Account resolveDestinationAccount(String accountNumber, String ifsc) {
        if (!BANKSPHERE_IFSC.equalsIgnoreCase(ifsc)) {
            throw new UnsupportedIfscException(ifsc);
        }
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RecipientNotFoundException(accountNumber));
    }

    private static String transferLegDescription(String customerDescription, String defaultText) {
        return (customerDescription == null || customerDescription.isBlank()) ? defaultText : customerDescription;
    }

    private void credit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
    }

    private void debit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(amount));
    }

    private void requireSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(account.getId(), account.getBalance(), amount);
        }
    }

    private Account findAccountOrThrow(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
    }

    /**
     * Checked after the existence lookup (unlike customer-service's
     * identical-looking check): account ids are random, high-entropy
     * UUIDs, not guessable identifiers like an email address, so
     * confirming "this account id exists" via a 404-vs-403 distinction
     * isn't a meaningful information leak the way it would be for a
     * login or customer-profile lookup. See docs/security/authorization.md.
     */
    private void requireOwnership(Account account, UUID requestingCustomerId) {
        if (!account.getCustomerId().equals(requestingCustomerId)) {
            throw new AccountAccessDeniedException(account.getId());
        }
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%012d", Math.abs(RANDOM.nextLong() % 1_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
