# ADR-004: Internal Account-to-Account Transfer (Phase 7A)

**Status:** Accepted for Phase 7A

## Context

BankSphere had deposit and withdrawal on a single account (`POST /api/v1/accounts/{id}/deposit`/`withdraw`) but no way to move money between two accounts atomically. Phase 7A adds exactly that — `POST /api/v1/accounts/transfer` inside account-service — and nothing else: no Transfer Service, no Kafka, no Outbox, no payment switch, no notification-service integration. Those are explicitly later phases (see the roadmap's dependency chain: Customer → Transfer Service → Account Service → Outbox → Kafka → Transaction/Ledger Service → Notification/Audit).

The hard requirement is atomicity: the debit and credit must both happen or neither happens, even though account-service also talks to a *separate* database (transaction-service's) to record the ledger. This ADR documents where the atomicity boundary actually is, and the five design decisions that follow from it.

## Decision 1 — The transfer lives in account-service, not a new Transfer Service

Account-service already owns account balances and already enforces the one authorization rule that matters here (does the caller own this account?). A separate Transfer Service today would need to call back into account-service for the actual balance mutation anyway, re-opening exactly the two-independent-transactions problem this phase exists to avoid, for no benefit at this scale. `AccountService.transfer(...)` is added alongside `deposit`/`withdraw` as a third balance-mutating operation on the same service, same database, same `@Transactional` boundary discipline. A dedicated Transfer Service is expected once transfers need to orchestrate across a payment switch or interbank rails — not needed for a same-database, same-service operation.

## Decision 2 — Atomicity boundary is the account-service database only, never faked wider

`AccountServiceImpl.transfer(...)` is one `@Transactional` method: load source, load destination, validate, debit source, credit destination, `saveAndFlush` both. If anything after the debit throws, Spring rolls back the whole method — including a write that had already been flushed (sent to Postgres) but not yet committed. This is true, ordinary single-database ACID; nothing distributed is involved.

Recording the two `TRANSFER` ledger legs in transaction-service (a *different* database) happens **after** both balance writes have already succeeded, using the same fire-and-forget `TransactionClient` deposit/withdraw already uses — see Decision 5. This is a deliberate, honestly-documented consistency boundary, not something this phase pretends to solve. A future Outbox/Kafka phase is what actually closes it (see "Deferred to a later phase" below) — Phase 7A does not simulate that architecture with, e.g., a two-phase commit or a synchronous cross-service rollback, both of which the task explicitly ruled out.

## Decision 3 — Deterministic account ordering, not pessimistic locking, to avoid deadlock

Two accounts are involved, and two customers could simultaneously run opposite-direction transfers (A→B and B→A). If each transaction touched the two rows in its own "natural" source-then-destination order, they could lock in opposite order and genuinely deadlock at the Postgres level — a real risk `@Version` alone does nothing to prevent (optimistic locking detects a *lost update* at flush time; it doesn't influence *lock acquisition order* at all).

**Decision:** `transfer()` always mutates and flushes the two accounts in a fixed order — ascending `UUID.compareTo()`, independent of which one is source vs destination:

```java
boolean sourceIsFirst = sourceId.compareTo(destinationId) < 0;
UUID firstId = sourceIsFirst ? sourceId : destinationId;
UUID secondId = sourceIsFirst ? destinationId : sourceId;
...
accountRepository.saveAndFlush(first);
accountRepository.saveAndFlush(second);
```

`saveAndFlush` (not `save`) is used deliberately — it forces the first UPDATE to actually reach Postgres, and its row lock to be acquired, before the second UPDATE is even issued, rather than relying on Hibernate's own unspecified automatic flush ordering. With both directions of a conflicting pair always touching the same account first, one transaction simply waits for the other's lock on that first row instead of each holding what the other wants.

**Why not pessimistic locking:** the task's own instruction was not to introduce it unless code inspection proved it necessary. Nothing in the existing codebase uses `SELECT ... FOR UPDATE` or `@Lock(PESSIMISTIC_WRITE)` anywhere — every service relies on `@Version` alone (see `Account.version`, already present before this phase). Deterministic ordering removes the deadlock risk without changing that established concurrency model; `@Version` remains the sole mechanism that actually rejects a lost update. See Decision 4 for how that was proven, not just asserted.

## Decision 4 — Proving rollback for real, not with Mockito alone

A Mockito test can prove "the code didn't call `save()` on the destination after an exception" — it cannot prove Postgres actually rolled back an already-flushed write. This project has no Testcontainers and no prior `@SpringBootTest`-against-real-Postgres integration test in any service (only Mockito unit tests and `@WebMvcTest` slices).

**What was added:** two `@SpringBootTest` integration tests under `backend/services/account-service/src/test/java/com/banksphere/account/integration/`, run via `maven-failsafe-plugin` during `mvn verify` (not `mvn test` — Surefire still only runs the Mockito/`@WebMvcTest` suite, so `mvn test` stays fast and infrastructure-free, matching the project's existing convention). Both skip (not fail) via `Assumptions.assumeTrue` if no Postgres is reachable at the same `DB_HOST`/`DB_PORT` env vars the service itself uses — see `PostgresAssumptions`.

**`AccountTransferRollbackIT`** proves atomicity with a genuine, non-simulated conflict, entirely from outside the production code (no test-only hook was added to `AccountServiceImpl`):
1. Source (10,000) and destination (2,000) are seeded and committed, with ids chosen so destination sorts after source — guaranteeing (per Decision 3) destination is the *second* account flushed.
2. A second, independent JDBC connection opens its own transaction and issues `UPDATE accounts SET version = version + 1 WHERE id = :destinationId` — a real row-level write, held open (uncommitted), which takes a real Postgres row lock on the destination row.
3. `transfer(source → destination, 5,000)` runs on a background thread. Its debit of source flushes cleanly (uncontended). Its credit of destination blocks on the real row lock.
4. The second connection commits, genuinely bumping the destination row's version. The blocked UPDATE inside `transfer()` proceeds — but its `WHERE ... AND version = <stale>` no longer matches any row, so Hibernate raises `ObjectOptimisticLockingFailureException`.
5. Spring's `@Transactional` proxy rolls back the *whole* transfer transaction on that unchecked exception, including the source debit that had already been flushed but never committed.
6. Both balances, re-read on a fresh query, are asserted unchanged.

This is real Postgres row locking plus real Hibernate optimistic-version checking — not a fake.

**`AccountTransferConcurrencyIT`** proves the safety invariant under real concurrent load: two threads, aligned via a `CyclicBarrier`, each attempt a 7,000 transfer against a shared 10,000 balance. Exactly one succeeds; the other fails safely — either `ObjectOptimisticLockingFailureException` (both read the balance before either committed) or `InsufficientBalanceException` (the second thread's read already saw the reduced balance). **Which specific failure mode occurs is not asserted** — it depends on real thread/database scheduling and is legitimately nondeterministic run to run; both are correct outcomes. What *is* asserted, deterministically, is the invariant: exactly one success, final balance exactly `10000 − 7000`, never negative. This is documented here as an accepted characteristic of a genuine concurrency test, not a flaky one to be "fixed."

Both tests were run against the project's live `banksphere-postgres` Docker container (the same one used for this project's existing live smoke-testing pattern) and passed — see the Phase 7A engineering journal entry for the actual run output.

## Decision 5 — Ledger recording stays best-effort, exactly like ADR-001

`TransactionClient.recordTransaction(...)` already never rethrows for deposit/withdraw (its javadoc: "a failed recording is logged so it does not roll back the balance change that already succeeded"). Transfer reuses the same client, called twice — once per leg — only *after* both `saveAndFlush` calls have already succeeded within the transaction:

```java
transactionClient.recordTransaction(new TransactionRecordRequest(source.getId(), "TRANSFER", amount, currency, ...), bearerToken);
transactionClient.recordTransaction(new TransactionRecordRequest(destination.getId(), "TRANSFER", amount, currency, ...), bearerToken);
```

Each leg gets its own independent, transaction-service-generated `TXN-...` reference — transaction-service owns that reference format and this phase does not invent a competing one (confirmed by inspecting `TransactionServiceImpl.generateReference()` and `TransactionCreateRequest` before writing any code). The response's `transferId` is an account-service-local correlation id for this response only, explicitly documented (in `TransferResponse`'s javadoc) as *not* a ledger reference. **The two legs are not currently linked by a shared id in the ledger itself** — this is an accepted limitation of this phase, not an oversight; closing it is exactly the kind of thing an Outbox-recorded transfer event would carry a real correlation id for, once that phase exists.

This was also verified live: with transaction-service unreachable during one integration-test run, both `recordTransaction` calls logged a connection error and the transfer still completed and returned `200` — the balance change was never rolled back by a downstream ledger failure. See the engineering journal for the exact log output.

## Decision 6 — Currency mismatch is a hard reject, not silently converted

There is no FX/conversion capability anywhere in BankSphere. `transfer()` compares `source.getCurrency()` to `destination.getCurrency()` after the active-status checks and throws `CurrencyMismatchException` (mapped to `422`) on any mismatch. This is not a placeholder for a future conversion feature — it is the correct behavior for a system with no conversion logic, and is expected to remain a hard reject even once cross-currency transfers become a real, separately-scoped feature (with real conversion, real rates, and its own review).

## Decision 7 — `PUT`-style identifying-field changes don't apply here; transfer never edits an account

Unlike `beneficiary-service`'s `UpdateBeneficiaryRequest` (ADR-003, Decision 4), there's no analogous concern here — `transfer()` never mutates `accountNumber`, `currency`, or any identifying field, only `balance`. Noted only to make explicit that this ADR considered and ruled out an "editable" angle, not that one exists.

## Validation and status-code ordering

`transfer()` checks, in this order: same-account (400, before any DB access — a pure input check) → source/destination existence (404) → source ownership (403) → source/destination active status (422) → currency match (422) → sufficient balance (422). Amount null/zero/negative is rejected earlier still, by Bean Validation on `TransferRequest` (400), mirroring the existing `AmountRequest` convention rather than a service-level check. This ordering — cheapest/most fundamental checks first — is a design choice this ADR makes explicit since the task's own instructions specified the checks but not their relative precedence.

## Consequences

- `POST /api/v1/accounts/transfer` never lets a caller move money out of an account they don't own; the destination account may belong to any customer, exactly as specified.
- A transfer either fully applies (both balances updated, both ledger legs eventually recorded on a best-effort basis) or fully fails to apply (no balance changed) — proven with a real database, not assumed.
- Existing `deposit`/`withdraw` behavior and their existing tests are unchanged in outcome; internally they now call the same `credit`/`debit`/`requireSufficientBalance` helpers `transfer()` uses, removing duplicated arithmetic.

## Deferred to a later phase (explicitly not built now)

Transfer Service as a separate microservice; Kafka event publishing; an Outbox table/pattern for reliable cross-service ledger recording; a payment switch; interbank transfer; notification-service integration on transfer completion; any frontend transfer UI; cross-currency conversion. All are named in the roadmap's future architecture (Customer → Transfer Service → Account Service → Outbox → Kafka → Transaction/Ledger Service → Notification/Audit) and none are started by this ADR or this phase.
