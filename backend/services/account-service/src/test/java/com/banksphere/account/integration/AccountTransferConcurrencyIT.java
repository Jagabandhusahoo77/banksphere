package com.banksphere.account.integration;

import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.entity.Account;
import com.banksphere.account.entity.AccountStatus;
import com.banksphere.account.entity.AccountType;
import com.banksphere.account.exception.InsufficientBalanceException;
import com.banksphere.account.repository.AccountRepository;
import com.banksphere.account.service.AccountService;
import com.banksphere.account.service.AccountServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the safety invariant under REAL concurrent load against a real
 * Postgres instance: two simultaneous transfers that together exceed the
 * source's balance can never both succeed, and the balance can never go
 * negative — closing the "two 7,000 transfers against a 10,000 balance"
 * scenario from ADR-004.
 *
 * <p><b>Which specific exception the losing transfer throws is not
 * asserted</b> — depending on real timing/interleaving, it is either
 * {@link ObjectOptimisticLockingFailureException} (both threads read the
 * balance before either committed, so the second one's flush conflicts)
 * or {@link InsufficientBalanceException} (the first transfer fully
 * committed before the second thread's {@code findById} ran, so the
 * second thread correctly sees the already-reduced balance and rejects
 * up front). Both are correct, safe outcomes under the existing
 * optimistic-locking design; which one occurs on a given run is
 * inherently nondeterministic and depends on real thread/database
 * scheduling — see ADR-004 for why this is documented as an accepted
 * limitation rather than "flaky." What IS asserted, and IS deterministic,
 * is the invariant: exactly one transfer's worth of money moved, and the
 * balance is never negative.
 */
@SpringBootTest
class AccountTransferConcurrencyIT {

    private static final Logger log = LoggerFactory.getLogger(AccountTransferConcurrencyIT.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeAll
    static void checkPostgresReachable() {
        PostgresAssumptions.assumeReachable();
    }

    @Test
    void transfer_allowsOnlyOneOfTwoConflictingConcurrentTransfers_toConsumeTheAvailableBalance() throws Exception {
        UUID sourceOwnerId = UUID.randomUUID();
        UUID sourceId = seedAccount(sourceOwnerId, new BigDecimal("10000.0000")).id();
        SeededAccount destination1 = seedAccount(UUID.randomUUID(), new BigDecimal("0.0000"));
        SeededAccount destination2 = seedAccount(UUID.randomUUID(), new BigDecimal("0.0000"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        Callable<String> transfer1 = () -> attemptTransfer(startBarrier, sourceId, destination1.accountNumber(), sourceOwnerId);
        Callable<String> transfer2 = () -> attemptTransfer(startBarrier, sourceId, destination2.accountNumber(), sourceOwnerId);

        try {
            Future<String> future1 = executor.submit(transfer1);
            Future<String> future2 = executor.submit(transfer2);

            String outcome1 = future1.get(15, TimeUnit.SECONDS);
            String outcome2 = future2.get(15, TimeUnit.SECONDS);

            List<String> outcomes = List.of(outcome1, outcome2);
            long successes = outcomes.stream().filter("SUCCESS"::equals).count();
            long safeFailures = outcomes.stream()
                    .filter(o -> o.equals("OPTIMISTIC_LOCK_CONFLICT") || o.equals("INSUFFICIENT_BALANCE"))
                    .count();

            log.info("Concurrent transfer outcomes: {}", outcomes);

            assertThat(successes)
                    .as("exactly one of the two 7,000 transfers against a 10,000 balance must succeed")
                    .isEqualTo(1);
            assertThat(safeFailures)
                    .as("the other must fail safely (optimistic-lock conflict or insufficient balance), not silently succeed")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Account reloadedSource = accountRepository.findById(sourceId).orElseThrow();
        assertThat(reloadedSource.getBalance())
                .as("only one 7,000 transfer can have been applied")
                .isEqualByComparingTo("3000.0000");
        assertThat(reloadedSource.getBalance().signum())
                .as("balance must never go negative")
                .isGreaterThanOrEqualTo(0);
    }

    private String attemptTransfer(CyclicBarrier startBarrier, UUID sourceId, String destinationAccountNumber, UUID ownerId) {
        try {
            startBarrier.await(10, TimeUnit.SECONDS);
            accountService.transfer(
                    new TransferRequest(sourceId, destinationAccountNumber, AccountServiceImpl.BANKSPHERE_IFSC,
                            new BigDecimal("7000.00"), "concurrency-it", null, null),
                    ownerId, "unused-in-this-test");
            return "SUCCESS";
        } catch (ObjectOptimisticLockingFailureException ex) {
            return "OPTIMISTIC_LOCK_CONFLICT";
        } catch (InsufficientBalanceException ex) {
            return "INSUFFICIENT_BALANCE";
        } catch (Exception ex) {
            throw new RuntimeException("Unexpected transfer failure mode: " + ex, ex);
        }
    }

    private record SeededAccount(UUID id, String accountNumber) {
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 12 digits — TransferRequest's destinationAccountNumber now validates this exactly, matching the real generator's format. */
    private static String randomAccountNumber() {
        return String.format("%012d", Math.abs(RANDOM.nextLong() % 1_000_000_000_000L));
    }

    private SeededAccount seedAccount(UUID ownerId, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        String accountNumber = randomAccountNumber();
        accountRepository.saveAndFlush(Account.builder()
                .id(id)
                .customerId(ownerId)
                .accountNumber(accountNumber)
                .ifsc(AccountServiceImpl.BANKSPHERE_IFSC)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .currency("INR")
                .status(AccountStatus.ACTIVE)
                .build());
        return new SeededAccount(id, accountNumber);
    }
}
