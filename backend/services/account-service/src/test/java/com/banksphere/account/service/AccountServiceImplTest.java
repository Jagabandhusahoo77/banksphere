package com.banksphere.account.service;

import com.banksphere.account.dto.AccountCreateRequest;
import com.banksphere.account.dto.AccountResponse;
import com.banksphere.account.dto.AmountRequest;
import com.banksphere.account.dto.ResolveRecipientRequest;
import com.banksphere.account.dto.ResolveRecipientResponse;
import com.banksphere.account.dto.TransactionRecordRequest;
import com.banksphere.account.dto.TransactionRecordResult;
import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.dto.TransferResponse;
import com.banksphere.account.entity.Account;
import com.banksphere.account.entity.AccountStatus;
import com.banksphere.account.entity.AccountType;
import com.banksphere.account.exception.AccountAccessDeniedException;
import com.banksphere.account.exception.AccountNotActiveException;
import com.banksphere.account.exception.AccountNotFoundException;
import com.banksphere.account.exception.CurrencyMismatchException;
import com.banksphere.account.exception.InsufficientBalanceException;
import com.banksphere.account.exception.BranchScopeViolationException;
import com.banksphere.account.exception.RecipientNotFoundException;
import com.banksphere.account.exception.UnsupportedIfscException;
import com.banksphere.account.repository.AccountRepository;
import com.banksphere.account.security.EmployeePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final String TOKEN = "test-bearer-token";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionClient transactionClient;

    @Mock
    private TransferIdempotencyService idempotencyService;

    @Mock
    private StepUpPolicy stepUpPolicy;

    @Mock
    private StepUpVerificationClient stepUpVerificationClient;

    private AccountServiceImpl accountService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        // stepUpPolicy is a plain @Mock, never stubbed here: every existing
        // transfer test uses an amount well below the real default
        // threshold, and an unstubbed Mockito boolean method already
        // returns false — i.e. "no step-up required" — which is exactly
        // the pre-Phase-9D behavior these tests assert against. A real
        // ObjectMapper (not mocked) is used for idempotency snapshot
        // (de)serialization — registerModule(JavaTimeModule) mirrors
        // Spring Boot's own auto-configured ObjectMapper bean (which is
        // what AccountServiceImpl is actually injected with in
        // production, via JacksonAutoConfiguration — jackson-datatype-
        // jsr310 is already on the classpath transitively through
        // spring-boot-starter-web), needed since TransferResponse carries
        // an Instant field.
        accountService = new AccountServiceImpl(accountRepository, transactionClient,
                idempotencyService, stepUpPolicy, stepUpVerificationClient, new ObjectMapper().registerModule(new JavaTimeModule()));
        customerId = UUID.randomUUID();
    }

    private Account activeAccount(BigDecimal balance) {
        return Account.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .accountNumber("123456789012")
                .ifsc(AccountServiceImpl.BANKSPHERE_IFSC)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();
    }

    private Account account(UUID id, UUID ownerId, BigDecimal balance, String currency, AccountStatus status) {
        return account(id, ownerId, balance, currency, status, "123456789012");
    }

    private Account account(UUID id, UUID ownerId, BigDecimal balance, String currency, AccountStatus status, String accountNumber) {
        return Account.builder()
                .id(id)
                .customerId(ownerId)
                .accountNumber(accountNumber)
                .ifsc(AccountServiceImpl.BANKSPHERE_IFSC)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .currency(currency)
                .status(status)
                .build();
    }

    private void stubSaveAndFlushAsIdentity() {
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAccount_persistsAccountOwnedByAuthenticatedCaller_whenNoInitialDepositGiven() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountCreateRequest request = new AccountCreateRequest(AccountType.SAVINGS, "USD", null);
        AccountResponse response = accountService.createAccount(request, customerId, TOKEN);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.accountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void createAccount_recordsInitialDepositTransaction_whenInitialDepositIsPositive() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountCreateRequest request = new AccountCreateRequest(AccountType.SAVINGS, "USD", new BigDecimal("100.00"));
        AccountResponse response = accountService.createAccount(request, customerId, TOKEN);

        assertThat(response.balance()).isEqualByComparingTo("100.00");
        verify(transactionClient).recordTransaction(any(TransactionRecordRequest.class), eq(TOKEN));
    }

    @Test
    void createAccount_assignsA12DigitServerGeneratedAccountNumber_neverSuppliedByTheClient() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        // AccountCreateRequest has no accountNumber component at all — there
        // is structurally no way for a caller to supply one. This test
        // documents and verifies the resulting value's shape instead.
        AccountCreateRequest request = new AccountCreateRequest(AccountType.SAVINGS, "USD", null);
        AccountResponse response = accountService.createAccount(request, customerId, TOKEN);

        assertThat(response.accountNumber()).matches("\\d{12}");
    }

    @Test
    void createAccount_assignsTheSingleBankSphereIfsc_neverSuppliedByTheClientAndNeverRandomized() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountCreateRequest request = new AccountCreateRequest(AccountType.SAVINGS, "USD", null);
        AccountResponse response1 = accountService.createAccount(request, customerId, TOKEN);
        AccountResponse response2 = accountService.createAccount(request, customerId, TOKEN);

        // Same IFSC on every account — it identifies the bank/branch, not
        // the individual account, and BankSphere has no branch model yet.
        assertThat(response1.ifsc()).isEqualTo(AccountServiceImpl.BANKSPHERE_IFSC);
        assertThat(response2.ifsc()).isEqualTo(AccountServiceImpl.BANKSPHERE_IFSC);
        assertThat(response1.ifsc()).isEqualTo(response2.ifsc());
    }

    @Test
    void createAccount_retriesGeneration_whenTheFirstCandidateAccountNumberAlreadyExists() {
        // existsByAccountNumber returns true once (a collision), then false
        // — the account-number generation loop must retry rather than
        // persisting a duplicate.
        when(accountRepository.existsByAccountNumber(any())).thenReturn(true, false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountCreateRequest request = new AccountCreateRequest(AccountType.SAVINGS, "USD", null);
        AccountResponse response = accountService.createAccount(request, customerId, TOKEN);

        assertThat(response.accountNumber()).matches("\\d{12}");
        verify(accountRepository, times(2)).existsByAccountNumber(any());
    }

    @Test
    void getAccount_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(id, customerId)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getAccount_throwsAccessDeniedException_whenRequestedByNonOwner() {
        Account account = activeAccount(BigDecimal.TEN);
        UUID someoneElse = UUID.randomUUID();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.getAccount(account.getId(), someoneElse))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void getAccountsByCustomer_throwsAccessDeniedException_whenRequestingAnotherCustomersAccounts() {
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> accountService.getAccountsByCustomer(customerId, someoneElse))
                .isInstanceOf(AccountAccessDeniedException.class);

        verify(accountRepository, never()).findByCustomerId(any());
    }

    @Test
    void getAccountsByCustomer_returnsAccounts_whenRequestingOwnAccounts() {
        Account account = activeAccount(BigDecimal.TEN);
        when(accountRepository.findByCustomerId(customerId)).thenReturn(List.of(account));

        List<AccountResponse> responses = accountService.getAccountsByCustomer(customerId, customerId);

        assertThat(responses).hasSize(1);
    }

    @Test
    void deposit_increasesBalanceAndRecordsTransaction_whenAccountIsActiveAndOwnedByCaller() {
        Account account = activeAccount(new BigDecimal("100.00"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.deposit(
                account.getId(), new AmountRequest(new BigDecimal("50.00"), "top-up"), customerId, TOKEN);

        assertThat(response.balance()).isEqualByComparingTo("150.00");
        verify(transactionClient).recordTransaction(any(TransactionRecordRequest.class), eq(TOKEN));
    }

    @Test
    void deposit_throwsAccessDeniedException_whenAccountNotOwnedByCaller() {
        Account account = activeAccount(new BigDecimal("100.00"));
        UUID someoneElse = UUID.randomUUID();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deposit(
                account.getId(), new AmountRequest(new BigDecimal("50.00"), null), someoneElse, TOKEN))
                .isInstanceOf(AccountAccessDeniedException.class);

        verify(accountRepository, never()).save(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void deposit_throwsAccountNotActiveException_whenAccountIsClosed() {
        Account account = activeAccount(BigDecimal.TEN);
        account.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.deposit(account.getId(), new AmountRequest(BigDecimal.ONE, null), customerId, TOKEN))
                .isInstanceOf(AccountNotActiveException.class);

        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void withdraw_decreasesBalanceAndRecordsTransaction_whenSufficientFundsAndOwnedByCaller() {
        Account account = activeAccount(new BigDecimal("100.00"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.withdraw(
                account.getId(), new AmountRequest(new BigDecimal("40.00"), "atm"), customerId, TOKEN);

        assertThat(response.balance()).isEqualByComparingTo("60.00");
        verify(transactionClient).recordTransaction(any(TransactionRecordRequest.class), eq(TOKEN));
    }

    @Test
    void withdraw_throwsAccessDeniedException_whenAccountNotOwnedByCaller() {
        Account account = activeAccount(new BigDecimal("100.00"));
        UUID someoneElse = UUID.randomUUID();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(
                account.getId(), new AmountRequest(new BigDecimal("40.00"), null), someoneElse, TOKEN))
                .isInstanceOf(AccountAccessDeniedException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void withdraw_throwsInsufficientBalanceException_whenAmountExceedsBalance() {
        Account account = activeAccount(new BigDecimal("30.00"));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(
                account.getId(), new AmountRequest(new BigDecimal("40.00"), null), customerId, TOKEN))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(accountRepository, never()).save(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    // ---- transfer() ----

    @Test
    void transfer_movesExactAmountBetweenAccounts_resolvingDestinationByAccountNumberAndIfsc() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID destinationOwner = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("10000.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, destinationOwner, new BigDecimal("2000.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        TransferResponse response = accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("5000.00"), "rent", null, null),
                customerId, TOKEN);

        assertThat(source.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("7000.00");
        assertThat(response.sourceAccountId()).isEqualTo(sourceId);
        // Never the internal destination id — see ADR-005.
        assertThat(response.destinationAccountNumber()).isEqualTo("222222222222");
        assertThat(response.destinationIfsc()).isEqualTo(AccountServiceImpl.BANKSPHERE_IFSC);
        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.transferId()).isNotNull();
    }

    @Test
    void transfer_preservesExactBigDecimalAmount_withFourDecimalPlaces() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("1000.0000"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("0.0000"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("123.4567"), null, null, null),
                customerId, TOKEN);

        assertThat(source.getBalance()).isEqualByComparingTo("876.5433");
        assertThat(destination.getBalance()).isEqualByComparingTo("123.4567");
    }

    @Test
    void transfer_recordsTwoTransferLedgerLegs_afterBothBalancesArePersisted() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN);

        // Proves ADR-001's best-effort guarantee extends to transfer: both
        // balance writes are flushed to the database BEFORE either ledger
        // call is made, so a transaction-service failure (which
        // TransactionClient never rethrows — see its javadoc) has no
        // opportunity to roll back a balance change that already
        // succeeded.
        InOrder order = inOrder(accountRepository, transactionClient);
        order.verify(accountRepository, times(2)).saveAndFlush(any(Account.class));
        order.verify(transactionClient, times(2)).recordTransaction(any(TransactionRecordRequest.class), eq(TOKEN));

        verify(transactionClient).recordTransaction(
                argThatTransactionType(sourceId, "TRANSFER"), eq(TOKEN));
        verify(transactionClient).recordTransaction(
                argThatTransactionType(destinationId, "TRANSFER"), eq(TOKEN));
    }

    @Test
    void transfer_usesHumanReadableAccountNumbersInDefaultLedgerDescriptions_notInternalIds() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN);

        verify(transactionClient).recordTransaction(
                org.mockito.ArgumentMatchers.argThat(r -> r.accountId().equals(sourceId)
                        && r.description() != null && r.description().contains("222222222222")),
                eq(TOKEN));
        verify(transactionClient).recordTransaction(
                org.mockito.ArgumentMatchers.argThat(r -> r.accountId().equals(destinationId)
                        && r.description() != null && r.description().contains("111111111111")),
                eq(TOKEN));
    }

    private static TransactionRecordRequest argThatTransactionType(UUID accountId, String type) {
        return org.mockito.ArgumentMatchers.argThat(r -> r.accountId().equals(accountId) && r.transactionType().equals(type));
    }

    @Test
    void transfer_flushesLowerCompareToAccountFirst_whenSourceIsTheLowerOne() {
        // Deliberately NOT asserting an ordering from how the UUID strings
        // look (e.g. "starts with 0" vs "starts with f") — UUID.compareTo
        // compares its two internal fields as SIGNED longs, so a UUID
        // starting with a high hex nibble can legitimately compare as
        // "less than" one starting with a low nibble. What matters for
        // AccountServiceImpl#transfer's deadlock-avoidance ordering is
        // only that the same pair always compares the same way — computed
        // here via the identical UUID.compareTo() the production code
        // uses, not assumed from string form.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID lower = a.compareTo(b) < 0 ? a : b;
        UUID higher = a.compareTo(b) < 0 ? b : a;
        Account lowerAccount = account(lower, customerId, new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account higherAccount = account(higher, customerId, new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(lower)).thenReturn(Optional.of(lowerAccount));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(higherAccount));
        stubSaveAndFlushAsIdentity();

        // source is the lower-compareTo account.
        accountService.transfer(
                new TransferRequest(lower, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("1.00"), null, null, null),
                customerId, TOKEN);

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).saveAndFlush(lowerAccount);
        order.verify(accountRepository).saveAndFlush(higherAccount);
    }

    @Test
    void transfer_flushesLowerCompareToAccountFirst_whenDestinationIsTheLowerOne() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID lower = a.compareTo(b) < 0 ? a : b;
        UUID higher = a.compareTo(b) < 0 ? b : a;
        Account lowerAccount = account(lower, customerId, new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account higherAccount = account(higher, customerId, new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(higher)).thenReturn(Optional.of(higherAccount));
        when(accountRepository.findByAccountNumber("111111111111")).thenReturn(Optional.of(lowerAccount));
        stubSaveAndFlushAsIdentity();

        // source is the HIGHER-compareTo account this time — flush order
        // must still be lower-compareTo-first, not source-first, since
        // the ordering exists purely to avoid opposite-direction deadlock,
        // independent of transfer direction.
        accountService.transfer(
                new TransferRequest(higher, "111111111111", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("1.00"), null, null, null),
                customerId, TOKEN);

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).saveAndFlush(lowerAccount);
        order.verify(accountRepository).saveAndFlush(higherAccount);
    }

    @Test
    void transfer_throwsAccessDeniedException_whenSourceNotOwnedByCaller() {
        UUID sourceId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        Account source = account(sourceId, someoneElse, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(AccountAccessDeniedException.class);

        // Fails fast on source ownership — never even attempts to resolve
        // the destination.
        verify(accountRepository, never()).findByAccountNumber(any());
        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void transfer_throwsNotFoundException_whenSourceMissing() {
        UUID sourceId = UUID.randomUUID();
        when(accountRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).findByAccountNumber(any());
    }

    @Test
    void transfer_throwsRecipientNotFoundException_whenDestinationAccountNumberDoesNotExist() {
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "999999999999", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(RecipientNotFoundException.class);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_throwsUnsupportedIfscException_whenDestinationIfscIsNotBankSphere() {
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", "HDFC0001234", new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(UnsupportedIfscException.class);

        // Never even looks up the account number — an external IFSC is
        // rejected before any account-number lookup, matching
        // resolveDestinationAccount's IFSC-first ordering.
        verify(accountRepository, never()).findByAccountNumber(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_throwsAccountNotActiveException_whenSourceInactive() {
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.CLOSED, "111111111111");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(AccountNotActiveException.class);

        verify(accountRepository, never()).findByAccountNumber(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_throwsAccountNotActiveException_whenDestinationInactive() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "USD", AccountStatus.INACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(AccountNotActiveException.class);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_throwsInsufficientBalanceException_whenAmountExceedsSourceBalance() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("30.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("40.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void transfer_throwsIllegalArgumentException_whenSourceAndDestinationAreTheSameAccount() {
        UUID accountId = UUID.randomUUID();
        Account soleAccount = account(accountId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(soleAccount));
        when(accountRepository.findByAccountNumber("111111111111")).thenReturn(Optional.of(soleAccount));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(accountId, "111111111111", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("10.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_throwsCurrencyMismatchException_whenSourceAndDestinationCurrenciesDiffer() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "USD", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "EUR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    // ---- Phase 9D: step-up gate + idempotency wrapper -------------------

    @Test
    void transfer_throwsStepUpRequiredException_whenPolicyRequiresItAndNoChallengeIdIsProvided() {
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("200000.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        when(stepUpPolicy.requiresStepUpForTransfer(any())).thenReturn(true);

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("100000.00"), null, null, null),
                customerId, TOKEN))
                .isInstanceOf(com.banksphere.account.exception.StepUpRequiredException.class);

        // Never mutated — the gate runs strictly before any balance write.
        assertThat(source.getBalance()).isEqualByComparingTo("200000.00");
        verify(accountRepository, never()).saveAndFlush(any());
        verify(stepUpVerificationClient, never()).confirmTransferStepUp(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transfer_succeeds_whenStepUpIsRequiredAndAChallengeIdIsProvidedAndConfirmed() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("200000.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        when(stepUpPolicy.requiresStepUpForTransfer(any())).thenReturn(true);
        stubSaveAndFlushAsIdentity();

        TransferResponse response = accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("100000.00"), null, challengeId, null),
                customerId, TOKEN);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(source.getBalance()).isEqualByComparingTo("100000.00");
        verify(stepUpVerificationClient).confirmTransferStepUp(
                challengeId, sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("100000.00"), "INR", TOKEN);
    }

    @Test
    void transfer_neverMutatesBalance_whenStepUpVerificationIsRejected() {
        UUID sourceId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("200000.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        when(stepUpPolicy.requiresStepUpForTransfer(any())).thenReturn(true);
        doThrow(new com.banksphere.account.exception.StepUpVerificationFailedException("rejected"))
                .when(stepUpVerificationClient).confirmTransferStepUp(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("100000.00"), null, challengeId, null),
                customerId, TOKEN))
                .isInstanceOf(com.banksphere.account.exception.StepUpVerificationFailedException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("200000.00");
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_doesNotCheckStepUpPolicy_whenNoIdempotencyOrStepUpConcernApplies_belowThreshold() {
        // Regression guard: a plain, no-step-up transfer (the overwhelming
        // majority of existing tests above) must not be affected by the
        // Phase 9D gate at all — this is really just re-confirming the
        // stepUpPolicy mock's unstubbed default (false) is what every
        // pre-Phase-9D transfer test above implicitly relies on.
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN);

        verify(stepUpVerificationClient, never()).confirmTransferStepUp(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transfer_returnsCachedSnapshot_withoutReExecuting_whenIdempotencyKeyAlreadyCompleted() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID cachedTransferId = UUID.randomUUID();
        TransferResponse cached = new TransferResponse(cachedTransferId, sourceId, "222222222222",
                AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), "INR", "COMPLETED", java.time.Instant.now());
        String snapshot = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(cached);
        when(idempotencyService.checkOrCreate(customerId, "retry-key"))
                .thenReturn(TransferIdempotencyResult.completed(snapshot));

        TransferResponse response = accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, "retry-key"),
                customerId, TOKEN);

        assertThat(response.transferId()).isEqualTo(cachedTransferId);
        // The whole point: no account lookup, no balance mutation, no
        // second ledger entry — this is a pure replay of the first result.
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).saveAndFlush(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void transfer_marksIdempotencyRecordCompleted_afterASuccessfulTransfer() {
        UUID sourceId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        when(idempotencyService.checkOrCreate(customerId, "fresh-key")).thenReturn(TransferIdempotencyResult.proceed(recordId));
        stubSaveAndFlushAsIdentity();

        TransferResponse response = accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, "fresh-key"),
                customerId, TOKEN);

        verify(idempotencyService).markCompleted(eq(recordId), eq(response.transferId()), any());
        verify(idempotencyService, never()).markFailed(any());
    }

    @Test
    void transfer_marksIdempotencyRecordFailed_andRethrows_whenTheUnderlyingTransferFails() {
        UUID sourceId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("10.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        when(idempotencyService.checkOrCreate(customerId, "doomed-key")).thenReturn(TransferIdempotencyResult.proceed(recordId));

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("5000.00"), null, null, "doomed-key"),
                customerId, TOKEN))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(idempotencyService).markFailed(recordId);
        verify(idempotencyService, never()).markCompleted(any(), any(), any());
    }

    @Test
    void transfer_propagatesIdempotencyConflictException_forAConcurrentDuplicateRequest() {
        when(idempotencyService.checkOrCreate(customerId, "racing-key"))
                .thenThrow(new com.banksphere.account.exception.IdempotencyConflictException());

        assertThatThrownBy(() -> accountService.transfer(
                new TransferRequest(UUID.randomUUID(), "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, "racing-key"),
                customerId, TOKEN))
                .isInstanceOf(com.banksphere.account.exception.IdempotencyConflictException.class);

        verify(accountRepository, never()).findById(any());
    }

    @Test
    void transfer_skipsIdempotencyBookkeepingEntirely_whenNoKeyIsSupplied() {
        UUID sourceId = UUID.randomUUID();
        Account source = account(sourceId, customerId, new BigDecimal("500.00"), "INR", AccountStatus.ACTIVE, "111111111111");
        Account destination = account(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));
        stubSaveAndFlushAsIdentity();

        accountService.transfer(
                new TransferRequest(sourceId, "222222222222", AccountServiceImpl.BANKSPHERE_IFSC, new BigDecimal("50.00"), null, null, null),
                customerId, TOKEN);

        verify(idempotencyService, never()).checkOrCreate(any(), any());
        verify(idempotencyService, never()).markCompleted(any(), any(), any());
        verify(idempotencyService, never()).markFailed(any());
    }

    // ---- resolveRecipient() ----

    @Test
    void resolveRecipient_returnsMinimalDetails_whenAccountExistsAndActive() {
        UUID destinationId = UUID.randomUUID();
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "INR", AccountStatus.ACTIVE, "222222222222");
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));

        ResolveRecipientResponse response = accountService.resolveRecipient(
                new ResolveRecipientRequest("222222222222", AccountServiceImpl.BANKSPHERE_IFSC));

        assertThat(response.accountNumber()).isEqualTo("222222222222");
        assertThat(response.ifsc()).isEqualTo(AccountServiceImpl.BANKSPHERE_IFSC);
        assertThat(response.bankName()).isEqualTo("BankSphere");
    }

    @Test
    void resolveRecipient_throwsRecipientNotFoundException_whenAccountNumberDoesNotExist() {
        when(accountRepository.findByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.resolveRecipient(
                new ResolveRecipientRequest("999999999999", AccountServiceImpl.BANKSPHERE_IFSC)))
                .isInstanceOf(RecipientNotFoundException.class);
    }

    @Test
    void resolveRecipient_throwsUnsupportedIfscException_whenIfscIsNotBankSphere() {
        assertThatThrownBy(() -> accountService.resolveRecipient(
                new ResolveRecipientRequest("222222222222", "HDFC0001234")))
                .isInstanceOf(UnsupportedIfscException.class);

        verify(accountRepository, never()).findByAccountNumber(any());
    }

    @Test
    void resolveRecipient_throwsAccountNotActiveException_whenAccountIsInactive() {
        UUID destinationId = UUID.randomUUID();
        Account destination = account(destinationId, UUID.randomUUID(), new BigDecimal("100.00"), "INR", AccountStatus.INACTIVE, "222222222222");
        when(accountRepository.findByAccountNumber("222222222222")).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> accountService.resolveRecipient(
                new ResolveRecipientRequest("222222222222", AccountServiceImpl.BANKSPHERE_IFSC)))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // ---- Phase 9B: employee-only lookup + employee deposit -------------

    private EmployeePrincipal tellerAt(String branchIfsc) {
        return new EmployeePrincipal(UUID.randomUUID(), "EMP000010", UUID.randomUUID(), branchIfsc,
                List.of("TELLER"), List.of("CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "CASH_DEPOSIT", "CASH_WITHDRAWAL"));
    }

    private EmployeePrincipal branchManagerAt(String branchIfsc) {
        return new EmployeePrincipal(UUID.randomUUID(), "EMP000020", UUID.randomUUID(), branchIfsc,
                List.of("BRANCH_MANAGER"), List.of("CASH_DEPOSIT"));
    }

    @Test
    void employeeLookupByAccountNumber_returnsFullAccountDetails_whenFound() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findByAccountNumber("617242043877")).thenReturn(Optional.of(account));

        AccountResponse response = accountService.employeeLookupByAccountNumber("617242043877");

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.balance()).isEqualByComparingTo("20000.00");
    }

    @Test
    void employeeLookupByAccountNumber_throwsRecipientNotFoundException_whenMissing() {
        when(accountRepository.findByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.employeeLookupByAccountNumber("999999999999"))
                .isInstanceOf(RecipientNotFoundException.class);
    }

    @Test
    void employeeLookupByCustomerId_returnsEveryAccountForThatCustomer() {
        when(accountRepository.findByCustomerId(customerId)).thenReturn(List.of(
                account(UUID.randomUUID(), customerId, new BigDecimal("1000.00"), "INR", AccountStatus.ACTIVE),
                account(UUID.randomUUID(), customerId, new BigDecimal("5000.00"), "INR", AccountStatus.ACTIVE)));

        List<AccountResponse> responses = accountService.employeeLookupByCustomerId(customerId);

        assertThat(responses).hasSize(2);
    }

    @Test
    void employeeDeposit_creditsAccount_whenTellerDepositsWithinTheirOwnBranch() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionClient.recordTransaction(any(TransactionRecordRequest.class), eq(TOKEN)))
                .thenReturn(Optional.of(new TransactionRecordResult(UUID.randomUUID(), "TXN-ABC123")));

        var response = accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("10000.00"), "CASH DEPOSIT - Branch HQ001"),
                tellerAt(AccountServiceImpl.BANKSPHERE_IFSC), TOKEN);

        assertThat(response.account().balance()).isEqualByComparingTo("30000.00");
        assertThat(response.transactionReference()).isEqualTo("TXN-ABC123");
    }

    @Test
    void employeeDeposit_throwsBranchScopeViolationException_whenTellerIsAtADifferentBranch() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("10000.00"), null),
                tellerAt("HDFC0001234"), TOKEN))
                .isInstanceOf(BranchScopeViolationException.class);

        verify(accountRepository, never()).save(any());
        verify(transactionClient, never()).recordTransaction(any(), any());
    }

    @Test
    void employeeDeposit_allowsBranchManager_regardlessOfTheAccountsIfsc() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionClient.recordTransaction(any(), any())).thenReturn(Optional.empty());

        var response = accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("500.00"), null),
                branchManagerAt("SOME0OTHERIFSC"), TOKEN);

        assertThat(response.account().balance()).isEqualByComparingTo("20500.00");
    }

    @Test
    void employeeDeposit_stillSucceeds_whenLedgerRecordingFails() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionClient.recordTransaction(any(), any())).thenReturn(Optional.empty());

        var response = accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("100.00"), null),
                tellerAt(AccountServiceImpl.BANKSPHERE_IFSC), TOKEN);

        assertThat(response.account().balance()).isEqualByComparingTo("20100.00");
        assertThat(response.transactionReference()).isNull();
    }

    @Test
    void employeeDeposit_throwsAccountNotFoundException_whenAccountDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(accountRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.employeeDeposit(missingId,
                new AmountRequest(new BigDecimal("100.00"), null), tellerAt(AccountServiceImpl.BANKSPHERE_IFSC), TOKEN))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void employeeDeposit_throwsAccountNotActiveException_whenAccountIsInactive() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.INACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("100.00"), null), tellerAt(AccountServiceImpl.BANKSPHERE_IFSC), TOKEN))
                .isInstanceOf(AccountNotActiveException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void employeeDeposit_neverTakesEmployeeIdentityFromTheAmountRequest_onlyFromTheAuthenticatedPrincipal() {
        // AmountRequest has no employeeId/branchId field at all — this test
        // exists to make that contract explicit and regression-proof: the
        // only way branch/identity information reaches this method is via
        // the EmployeePrincipal parameter, never anything parsed out of the
        // request body. See ADR-007.
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionClient.recordTransaction(any(), any())).thenReturn(Optional.empty());

        EmployeePrincipal principal = tellerAt(AccountServiceImpl.BANKSPHERE_IFSC);
        accountService.employeeDeposit(account.getId(), new AmountRequest(new BigDecimal("1.00"), null), principal, TOKEN);

        ArgumentCaptor<TransactionRecordRequest> captor = ArgumentCaptor.forClass(TransactionRecordRequest.class);
        verify(transactionClient).recordTransaction(captor.capture(), eq(TOKEN));
        assertThat(captor.getValue().accountId()).isEqualTo(account.getId());
    }

    @Test
    void employeeDeposit_throwsObjectOptimisticLockingFailureException_onConcurrentModification() {
        Account account = account(UUID.randomUUID(), customerId, new BigDecimal("20000.00"), "INR", AccountStatus.ACTIVE, "617242043877");
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Account.class, account.getId().toString()));

        assertThatThrownBy(() -> accountService.employeeDeposit(account.getId(),
                new AmountRequest(new BigDecimal("100.00"), null), tellerAt(AccountServiceImpl.BANKSPHERE_IFSC), TOKEN))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }
}
