package com.banksphere.account.integration;

import com.banksphere.account.dto.TransferRequest;
import com.banksphere.account.entity.Account;
import com.banksphere.account.entity.AccountStatus;
import com.banksphere.account.entity.AccountType;
import com.banksphere.account.repository.AccountRepository;
import com.banksphere.account.service.AccountService;
import com.banksphere.account.service.AccountServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves REAL PostgreSQL transaction rollback for
 * {@code AccountServiceImpl#transfer} — not a Mockito approximation. See
 * ADR-004's "Proving rollback for real" section for the full methodology;
 * summarized here:
 *
 * <ol>
 *   <li>Two accounts are persisted and committed: source (10,000) and
 *       destination (2,000), with ids chosen so destination's id sorts
 *       AFTER source's — meaning {@code transfer()}'s deterministic
 *       ascending-id flush order (see ADR-004) will flush destination
 *       second, which is what this test needs to force a conflict on.</li>
 *   <li>A second, independent JDBC connection opens its own transaction
 *       and issues {@code UPDATE accounts SET version = version + 1 WHERE
 *       id = :destinationId} — a real row-level write, NOT committed yet
 *       — so it holds a genuine Postgres row lock on the destination
 *       row.</li>
 *   <li>{@code transfer(source → destination, 5,000)} is started on a
 *       background thread. Its debit of source succeeds and flushes
 *       cleanly (source is untouched by the other connection). Its credit
 *       of destination blocks on Postgres's row lock.</li>
 *   <li>The second connection commits, bumping the destination row's real
 *       version. The blocked UPDATE inside {@code transfer()} then
 *       proceeds — but its {@code WHERE ... AND version = <stale>} clause
 *       no longer matches any row, so Hibernate raises
 *       {@link ObjectOptimisticLockingFailureException}.</li>
 *   <li>Spring's {@code @Transactional} proxy rolls back the WHOLE
 *       transfer transaction on that unchecked exception — including the
 *       source debit that had already been flushed (sent to Postgres) but
 *       never committed.</li>
 *   <li>Both balances, re-read fresh from the database on a brand-new
 *       query, are unchanged.</li>
 * </ol>
 *
 * No production code was modified to make this possible — the conflict is
 * manufactured entirely from this test, using genuine Postgres row
 * locking and genuine Hibernate optimistic-version checking. Runs only
 * during {@code mvn verify} (via maven-failsafe-plugin, not Surefire) and
 * only if a real Postgres is reachable — see {@link PostgresAssumptions}.
 */
@SpringBootTest
class AccountTransferRollbackIT {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void checkPostgresReachable() {
        PostgresAssumptions.assumeReachable();
    }

    @Test
    void transfer_rollsBackBothBalances_whenDestinationFlushHitsARealVersionConflict() throws Exception {
        UUID sourceOwnerId = UUID.randomUUID();

        // Generate two ids and assign the SMALLER to source, LARGER to
        // destination, so transfer()'s ascending-id flush order (ADR-004)
        // is guaranteed to flush destination second — the leg this test
        // needs to fail.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID sourceId = a.compareTo(b) < 0 ? a : b;
        UUID destinationId = a.compareTo(b) < 0 ? b : a;

        String destinationAccountNumber = seedAccount(destinationId, UUID.randomUUID(), new BigDecimal("2000.0000"));
        seedAccount(sourceId, sourceOwnerId, new BigDecimal("10000.0000"));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection conflictingConnection = dataSource.getConnection()) {
            conflictingConnection.setAutoCommit(false);
            try (Statement statement = conflictingConnection.createStatement()) {
                statement.executeUpdate("UPDATE accounts SET version = version + 1 WHERE id = '" + destinationId + "'");
            }
            // conflictingConnection now holds a real row lock on the
            // destination row, uncommitted.

            Future<?> transferFuture = executor.submit(() -> accountService.transfer(
                    new TransferRequest(sourceId, destinationAccountNumber, AccountServiceImpl.BANKSPHERE_IFSC,
                            new BigDecimal("5000.00"), "rollback-it", null, null),
                    sourceOwnerId, "unused-in-this-test"));

            // Generous, non-tight safety margin: transfer()'s in-memory
            // work before it reaches the destination UPDATE (a handful of
            // method calls, one prior UPDATE on an uncontended row) takes
            // microseconds in practice; this just avoids the main thread
            // racing ahead of it. Not required for correctness — Postgres
            // row locking is what actually makes the block real once
            // transfer() gets there.
            Thread.sleep(300);

            // Release the lock — the destination row's version is now
            // genuinely, visibly bumped past what transfer() already read.
            conflictingConnection.commit();

            assertThatThrownBy(() -> transferFuture.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
        }

        Account reloadedSource = accountRepository.findById(sourceId).orElseThrow();
        Account reloadedDestination = accountRepository.findById(destinationId).orElseThrow();

        assertThat(reloadedSource.getBalance())
                .as("source debit must have been rolled back with the rest of the transaction")
                .isEqualByComparingTo("10000.0000");
        assertThat(reloadedDestination.getBalance())
                .as("destination was never credited — its own flush is what failed")
                .isEqualByComparingTo("2000.0000");
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 12 digits — TransferRequest's destinationAccountNumber now validates this exactly, matching the real generator's format. */
    private static String randomAccountNumber() {
        return String.format("%012d", Math.abs(RANDOM.nextLong() % 1_000_000_000_000L));
    }

    private String seedAccount(UUID id, UUID ownerId, BigDecimal balance) {
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
        return accountNumber;
    }
}
