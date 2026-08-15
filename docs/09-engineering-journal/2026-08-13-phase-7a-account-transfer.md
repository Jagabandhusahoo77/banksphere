# 2026-08-13 — Phase 7A: Atomic Internal Account-to-Account Transfer

## Objective

Add a safe, atomic internal account-to-account transfer capability inside `account-service` — `POST /api/v1/accounts/transfer`. Explicitly scoped to a single-database, single-service operation: no Transfer Service, no Kafka, no Outbox, no payment switch, no notification-service integration, no frontend UI. Those are separate, later milestones per the roadmap's own future-architecture chain (Customer → Transfer Service → Account Service → Outbox → Kafka → Transaction/Ledger Service → Notification/Audit).

## Inspection before implementation

Per the task's own instruction, inspected before writing anything: `AccountServiceImpl`, `Account`, `AccountRepository`, `AccountController`, `GlobalExceptionHandler`, existing tests, `RestTransactionClient`/`TransactionClient`, the full security package, `application.yml`, `V1__create_accounts_table.sql`, `TransactionCreateRequest` and `TransactionServiceImpl.generateReference()` (to confirm transaction-service's `TXN-...` reference format before deciding how the transfer response should represent identity), and `beneficiary-service`'s equivalent conventions for comparison. No architecture was invented from scratch — every piece below extends an existing pattern.

## Design decisions (full detail in ADR-004)

1. **Lives in account-service**, not a new Transfer Service — it already owns balances and ownership checks; a separate service today would just call back into it, reopening the atomicity problem this phase exists to solve.
2. **Atomicity boundary is account-service's own database only.** One `@Transactional` method: load both accounts, validate, debit source, credit destination, `saveAndFlush` both. Ledger recording in transaction-service (a different database) happens only after both writes succeed, on the same best-effort, never-rethrows basis deposit/withdraw already use (ADR-001) — this phase does not fake a wider distributed transaction.
3. **Deterministic account ordering, not pessimistic locking.** The two accounts are always mutated and flushed in ascending-`UUID.compareTo()` order, independent of which is source/destination, specifically to prevent two opposite-direction transfers (A→B and B→A) from taking Postgres row locks in opposite order and deadlocking. `saveAndFlush` (not `save`) is used so this ordering is actually sent to Postgres in order, not left to Hibernate's unspecified internal flush ordering. `@Version` (already present on `Account`, untouched) remains the sole mechanism that rejects a lost update — nothing in this codebase used pessimistic locking before this phase, and code inspection found no reason to introduce it now.
4. **Currency mismatch is a hard `422` reject** — there is no FX capability anywhere in BankSphere.
5. **`PUT`-style concerns don't apply** — transfer never mutates an account's identifying fields, only its balance.

## A real bug caught during test-writing, not left in the code

While writing a unit test asserting the deterministic-ordering behavior, hardcoded UUID strings like `"00000000-...-0001"` (assumed "smaller") and `"ffffffff-...-fffe"` (assumed "larger") produced a failing test — not because the production code was wrong, but because **`UUID.compareTo()` compares its two internal fields as *signed* longs**, so a UUID starting with a high hex nibble (`f...`, negative as a signed long) can legitimately compare as "less than" one starting with `0` (non-negative). The production ordering logic is correct and deadlock-avoidance-sound regardless — all that actually matters is that `UUID.compareTo()` returns a consistent result for the same pair, which it does — but the test's assumption that hex-string appearance predicts comparison order was wrong. Fixed by computing "which one sorts first" via the same `UUID.compareTo()` the production code uses, at runtime, rather than assuming it from how two hardcoded strings look. Recorded here because it's a genuine, non-obvious Java gotcha worth remembering, not because it caused a production defect.

## Proving rollback for real (not Mockito)

This project has no Testcontainers and no prior `@SpringBootTest`-against-real-Postgres integration test in any service. Added `maven-failsafe-plugin` to `account-service/pom.xml`, bound to `integration-test`/`verify`, and two new `*IT.java` classes under a new `com.banksphere.account.integration` test package — Surefire (`mvn test`) still only runs the existing Mockito/`@WebMvcTest` suite, so `mvn test` stays fast and infrastructure-free; only `mvn verify` additionally requires a live Postgres, matching the project's established pattern of using live Docker infrastructure for its strongest verification layer. Both new tests skip (not fail) via `Assumptions.assumeTrue` if no Postgres is reachable at the standard `DB_HOST`/`DB_PORT` env vars.

**`AccountTransferRollbackIT`**: seeds source (10,000) and destination (2,000) with ids chosen so destination is guaranteed to be the second account flushed (per the deterministic-ordering design above). A second, independent JDBC connection opens its own uncommitted transaction and does `UPDATE accounts SET version = version + 1 WHERE id = :destinationId`, taking a genuine Postgres row lock. `transfer()` runs on a background thread: its source debit flushes cleanly, its destination credit blocks on the real lock. The test then commits the second connection, genuinely bumping the destination row's version; the blocked UPDATE inside `transfer()` proceeds but no longer matches its stale `WHERE version = ...` clause, so Hibernate throws `ObjectOptimisticLockingFailureException`, Spring rolls back the whole transaction, and both balances — re-read fresh — are asserted unchanged. No test-only hook was added to `AccountServiceImpl`; the conflict is manufactured entirely from outside using genuine Postgres locking and genuine Hibernate version-checking.

**`AccountTransferConcurrencyIT`**: two threads, aligned via a `CyclicBarrier`, each attempt a 7,000 transfer against a shared 10,000 balance. Exactly one must succeed; the other must fail safely. Which specific exception the loser throws (`ObjectOptimisticLockingFailureException` vs `InsufficientBalanceException`) is genuinely nondeterministic depending on real thread/database scheduling — both are correct outcomes, and this is documented explicitly as an accepted characteristic of the test, not flakiness to chase away. What's asserted deterministically: exactly one success, final balance exactly `10000 − 7000 = 3000`, never negative.

## Build and test results

- `mvn clean verify` (Docker `maven:3.9-eclipse-temurin-21` container, network-joined to the project's live `banksphere-postgres` container — no local Java/Maven available, consistent with every prior backend phase): **BUILD SUCCESS**.
- Surefire (`mvn test`): 42/42 unit + controller tests passing — 25 in `AccountServiceImplTest` (11 pre-existing + 14 new transfer cases), 17 in `AccountControllerTest` (7 pre-existing + 10 new transfer cases).
- Failsafe (`mvn verify`, real Postgres): 2/2 integration tests passing — `AccountTransferRollbackIT` and `AccountTransferConcurrencyIT`, both against the project's actual running Postgres container.
- A first attempt surfaced two real test bugs (not production bugs): the UUID-ordering assumption above, and a non-deterministic Mockito stub in a "destination missing" test that depended on lookup order — both fixed, both re-verified green.
- `transaction-service`: `mvn clean test` re-run to confirm no regressions from this phase (it wasn't touched) — 12/12 passing, unchanged from before this phase.

## Live smoke test

Rebuilt the `account-service` Docker image, swapped it into the running stack (same env vars as `docker-compose.yml`), and registered two real customers via customer-service. Exercised the new endpoint directly:

- Alice (10,000 INR) → Bob (2,000 INR), transfer 5,000 → `200`, Alice's balance `5000.0000`, Bob's `7000.0000`.
- Bob attempting to transfer *from* Alice's account → `403`.
- Source == destination → `400`.
- Amount exceeding available balance → `422`.
- No token → `401`.
- Nonexistent destination account → `404`.
- Zero amount → `400` (Bean Validation).
- Confirmed via transaction-service's own API that Alice's account shows both the original `DEPOSIT` (initial funding) and a new `TRANSFER` entry with its own independently generated `TXN-...` reference, description `"rent"` (the caller-supplied description), status `COMPLETED`.

Every result matched the documented design exactly.

## What was deliberately not done

Transfer Service, Kafka event publishing, an Outbox pattern, a payment switch, interbank transfer, notification-service integration, any frontend transfer UI, cross-currency conversion. All explicitly out of scope for this phase per the task's own repeated instruction, and named in ADR-004's "Deferred to a later phase" section.

## Environment constraints

Same as every prior backend phase: no local Java/Maven (Docker Maven container used for all builds/tests), no `docker compose` binary (the project's live containers, already running individually on `banksphere-net`, were used directly instead — including reusing the actual `banksphere-postgres` container for the new integration tests, rather than standing up a separate throwaway instance).
