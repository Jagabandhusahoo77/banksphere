# ADR-001: Account → Transaction Consistency

**Status:** Accepted for Phase 1

## Context

`account-service` owns account balances. `transaction-service` owns the transaction ledger (transaction history). They are separate Spring Boot applications, each with its own PostgreSQL database (`banksphere_account`, `banksphere_transaction`) — see [application-architecture.md](../application-architecture.md). Phase 1 explicitly excludes messaging infrastructure (Kafka) and any distributed-transaction machinery, so the two services can only communicate synchronously, over REST.

Every balance-changing operation should leave a corresponding entry in the transaction ledger: a deposit, a withdrawal, and (as of this review) an account's initial deposit at creation time.

## Current Design

`account-service` calls transaction-service directly and synchronously:

```text
AccountController.deposit()/withdraw()/createAccount()
        │
        ▼
AccountServiceImpl (@Transactional)
        │  1. load account (findAccountOrThrow)
        │  2. validate (status, sufficient balance)
        │  3. mutate balance, accountRepository.save(account)
        │  4. transactionClient.recordTransaction(...)  ← HTTP POST to transaction-service, still inside step 1-3's local transaction
        │  5. method returns → Spring commits the local (account) DB transaction
        ▼
RestTransactionClient
        │  RestClient.post("/api/v1/transactions") with a 3s connect / 5s read timeout
        │  catches RestClientException and logs — never rethrows
        ▼
transaction-service: POST /api/v1/transactions → INSERT into transactions, own local commit
```

Concretely, in `AccountServiceImpl`: the balance mutation and the call to `transactionClient.recordTransaction(...)` happen in the same `@Transactional` method, so **the HTTP call to transaction-service is made while the local database transaction that changed the balance is still open** — it is not deferred until after that transaction commits. `RestTransactionClient` treats the call as fully best-effort: on any `RestClientException` (timeout, connection refused, non-2xx, etc.) it logs and swallows the error rather than propagating it.

## Problem

This design has two related consequences:

1. **No atomicity.** There is no distributed transaction across the two databases. If the HTTP call to transaction-service fails or times out after the account balance has already been updated (step 3 above), the deposit/withdrawal still succeeds and is visible to the customer, but no ledger entry exists for it. The account balance and the transaction history can silently drift apart. There is currently no reconciliation or retry mechanism to detect or repair this.
2. **The external call executes inside the local transaction's boundary.** Because `recordTransaction()` runs before the `@Transactional` method returns, an external network round-trip (bounded to 5s by the read timeout added in this review) now sits inside the account database transaction's lifetime. Under load this extends how long a DB connection (and any row-level locks it holds) is checked out from account-service's connection pool, which is a real capacity/latency concern, though not a correctness one — the DB transaction only touches the `accounts` table and does not depend on the HTTP call's outcome.

What this design does **not** put at risk: the balance mutation itself. Validation (`requireActive`, the insufficient-balance check) happens before any mutation, and a failed `recordTransaction()` call never rolls back or blocks the balance change — it only means the ledger entry for that operation is missing.

## Why it is acceptable for Phase 1

- Phase 1's explicit goal is a working vertical slice — React → 3 Spring Boot services → PostgreSQL — with Kafka, an outbox pattern, and other messaging infrastructure deliberately deferred to a later phase.
- The failure mode is a **missing ledger entry**, not a **wrong balance**. Money is never double-counted, lost, or fabricated; the worst case is that a legitimate balance change is temporarily unexplained in the transaction history until manually reconciled.
- In practice, on a healthy local network (as in local development and, later, a single-cluster deployment), transaction-service being unreachable for the duration of a deposit/withdrawal call is rare, and the newly-added timeouts (§ below) bound how long a failure takes to surface.
- Introducing Kafka, an outbox table, or a saga/orchestrator now would pull in infrastructure and operational complexity (a broker, schema registry considerations, consumer idempotency, dead-letter handling) disproportionate to Phase 1's scope, and the task instructions for this phase explicitly prohibit adding Kafka.

## Future Design

None of the following are implemented yet — this section records options for a later phase, once messaging infrastructure is in scope.

**Future options:**
- **Transactional Outbox** — write the "transaction to be recorded" as a row in an outbox table inside the *same* local transaction as the balance mutation (atomic by construction, single database), then have a separate poller/relay publish it to transaction-service (or a queue) asynchronously, deleting/marking the outbox row once acknowledged.
- **Kafka** — account-service publishes a `BalanceChanged`/`DepositCompleted`/`WithdrawalCompleted` event to a topic (ideally via the outbox pattern above, to avoid dual-write problems); transaction-service consumes it and appends the ledger entry. Removes the synchronous coupling entirely.
- **Idempotent consumers** — a prerequisite for any retry-based fix: transaction-service's `POST /api/v1/transactions` would need a client-supplied idempotency key (e.g. an operation ID from account-service) so that retries or redelivery after a timeout don't create duplicate ledger entries.
- **Event-driven architecture** — more broadly, moving account-service and transaction-service to communicate exclusively through events rather than direct REST calls, so that new consumers (e.g. a future audit-service or notification-service) can subscribe without account-service knowing about them.

## Do NOT implement now

This ADR documents the decision and the trade-off. None of the Future Design options are implemented as part of this review — they are deferred to the phase where Kafka/messaging infrastructure is introduced.
