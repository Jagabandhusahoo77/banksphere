# 2026-08-11 — Phase 1 Implementation + Engineering Review

## Phase 1 objective

Build the first working vertical slice of BankSphere:

```text
React Frontend → Spring Boot REST APIs (customer, account, transaction) → PostgreSQL
```

as three independently deployable Spring Boot services with their own databases, a React + TypeScript frontend, Docker packaging, and a local Docker Compose environment — explicitly without any Phase 2+ technology (Kafka, Redis, Kubernetes, Terraform, CI/CD, Argo CD, or an observability stack).

This entry covers two work sessions: the initial implementation, and a dedicated engineering review performed afterward specifically to inspect the actual code (not the implementation's own self-report) for defects.

## Work completed

**Initial implementation:**
- `customer-service`, `account-service`, `transaction-service`: layered `controller → service → repository` Spring Boot apps, DTOs (never exposing JPA entities), Bean Validation, a `GlobalExceptionHandler` per service, Flyway migrations, Actuator with only `health`/`info` exposed.
- Account balances as `BigDecimal` / `NUMERIC(19,4)`, never floating point; optimistic locking via a JPA `@Version` column on `Account`.
- account-service calls transaction-service synchronously over REST to record ledger entries for deposits/withdrawals.
- React + TypeScript frontend (Vite, React Router, Axios, Tailwind): Login (placeholder), Dashboard, Accounts, Transactions, with an API service layer in `src/services/`.
- Dockerfiles (multi-stage: Maven/Node build stage → slim runtime stage) for all four components; `docker-compose.yml` wiring Postgres + the three services + the frontend on one Docker network.
- JUnit 5 + Mockito unit tests and `@WebMvcTest` controller tests per backend service.

**Engineering review (this entry's main focus):** re-read every service's source, the frontend, the Docker/Compose configuration, and the Flyway migrations from scratch — not relying on the implementation session's own completion report — specifically looking for defects in banking correctness, distributed-transaction handling, API/documentation drift, database design, frontend data flow, and security.

## Architecture

```text
React Frontend (Login / Dashboard / Accounts / Transactions)
        │  Axios, CORS-enabled (banksphere.cors.allowed-origins)
        ▼
customer-service (:8081) ─┐  account-service (:8082) ──► transaction-service (:8083)
        │                  │        │ (best-effort, synchronous, ADR-001)     │
        ▼                  │        ▼                                        ▼
banksphere_customer         │  banksphere_account                    banksphere_transaction
   (PostgreSQL)             │     (PostgreSQL)                           (PostgreSQL)
```

Full detail: [docs/architecture/application-architecture.md](../architecture/application-architecture.md). Database-per-service, no API Gateway, no messaging — see [docs/00-project-overview/scope.md](../00-project-overview/scope.md).

## Tests performed

**Backend (via `docker run maven:3.9-eclipse-temurin-21 mvn verify` — no local JDK/Maven available):**

| Service | Result (pre-review) | Result (post-review, after fixes) |
|---|---|---|
| customer-service | BUILD SUCCESS | BUILD SUCCESS, 10 tests, 0 failures |
| account-service | BUILD SUCCESS | BUILD SUCCESS, 11 tests, 0 failures |
| transaction-service | BUILD SUCCESS | BUILD SUCCESS, 8 tests, 0 failures |

Two new tests were added during the review specifically to lock in fixes: `createAccount_recordsInitialDepositTransaction_whenInitialDepositIsPositive` (account-service) and `getTransactionsByAccount_defaultsToNewestFirst_whenNoSortRequested` (transaction-service).

**Frontend:** `tsc -b` (strict type-check) and `vite build` (production build) both succeeded with no errors, before and after the review's type fixes.

**Docker / integration (manual, since `docker compose` was unavailable — see Problems below):**
- Built all four images (`docker build`) — all succeeded.
- Ran Postgres + all three services + the frontend by hand (`docker network create` + `docker run`, matching what `docker-compose.yml` declares).
- Exercised the real, running stack with `curl` against real PostgreSQL: created a customer, opened an account with an initial deposit, deposited, withdrew, triggered insufficient-balance (`422`), triggered validation errors (`400`), triggered not-found (`404`), triggered duplicate-email (`409`).
- Re-ran the same exercise after the review's fixes, plus new checks: a CORS preflight (`OPTIONS`) request from the frontend's origin (succeeds, correct `Access-Control-Allow-Origin`) and from a disallowed origin (`403 Invalid CORS request`); an account opened with a positive `initialDeposit` now shows a `DEPOSIT` transaction in the ledger; four sequential deposits come back in guaranteed newest-first order.

## Problems encountered and solutions

1. **No CORS configuration existed anywhere.** Found by re-reading `apiClient.ts` (a separate-origin Axios client) against the backend services and grepping for any CORS-related code — there was none. This would have silently blocked every frontend→backend call in a real browser, even though direct `curl` testing (which doesn't enforce CORS) had passed cleanly in the original implementation session and made it look like the API layer was fully verified. **Fix:** added a `WebMvcConfigurer`-based `CorsConfig` to all three services, origin list driven by `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`), verified against both an allowed and a disallowed origin.
2. **Transaction history had no default sort.** `TransactionController.getTransactionsByAccount` used `@PageableDefault(size = 20)` with no `sort`, so "newest first" (as documented) wasn't actually guaranteed by the database — pagination order was effectively undefined. **Fix:** `@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)`; re-verified with 5 real transactions in the live stack.
3. **Account creation's `initialDeposit` didn't create a ledger entry**, unlike `deposit()`/`withdraw()`. A starting balance existed with no corresponding transaction record explaining it. **Fix:** `createAccount()` now calls the same `transactionClient.recordTransaction(...)` path when `initialDeposit > 0`, described as `"Initial deposit"`.
4. **Unique-constraint races surfaced as `500`, not `409`.** e.g. two concurrent customer-creation requests with the same email could both pass the `existsByEmailIgnoreCase` pre-check; the second's `INSERT` would then violate the DB unique index and, with no `DataIntegrityViolationException` handler registered, fall through to the generic `500` handler. **Fix:** added a handler mapping `DataIntegrityViolationException` → `409 Conflict` in all three services.
5. **No timeout on account-service's call to transaction-service.** The `RestClient` bean had no explicit timeout, so an unreachable or slow transaction-service could hang the HTTP call indefinitely — and, since the call executes inside account-service's own `@Transactional` method, would hold a DB connection open for that entire duration. **Fix:** explicit 3s connect / 5s read timeout via `ClientHttpRequestFactorySettings`.
6. **Postgres init script hard-coded `"banksphere"` as the database owner** instead of using `$POSTGRES_USER`, so it would fail if `DB_USERNAME` was ever customized away from the default, despite `.env.example` presenting it as something to change. **Fix:** replaced the plain `.sql` file with a `.sh` script (the standard pattern for the official Postgres image) that substitutes `$POSTGRES_USER` at runtime.
7. **Frontend TypeScript types declared monetary fields as `string`** (`Account.balance`, `Transaction.amount`, etc.) when the actual JSON responses are numbers (confirmed via the live `curl` output, e.g. `"balance":100.00`, unquoted). No runtime bug today (`formatMoney` defensively handles both), but a real foot-gun for Phase 2's UI work. **Fix:** corrected all monetary field types to `number`; `tsc -b` still passes.
8. **Minor cleanup:** removed `InvalidAmountException` (defined, wired into the exception handler, but never actually thrown anywhere — dead code, since amount validation happens via `@DecimalMin`/`@Digits`), and aligned a cosmetic `DB_PASSWORD` default string across `application.yml` files to match `docker-compose.yml`.
9. **`docker compose` (v1 and v2) is not installed** in this environment, and the hosts needed to install it (GitHub Releases, PyPI's `pip` was also unavailable) were unreachable within the sandbox's network restrictions. **Resolution:** validated the Compose file's structure (`python3 -c "import yaml..."`), then proved the equivalent stack works by building each image with `docker build` and wiring them together manually with `docker network create` + `docker run`, passing the exact same environment variables `docker-compose.yml` declares.
10. **No browser is available** in this environment. Frontend correctness was verified via `tsc -b`, `vite build`, and — specifically for the CORS fix — `curl`-based preflight (`OPTIONS`) requests that inspect the actual `Access-Control-*` response headers a browser would check. Clicking through the real UI was not possible and is called out explicitly rather than assumed.

## Known limitations (unchanged by this review — accepted, not fixed)

- **Account → transaction consistency is best-effort**, not a distributed transaction. See [ADR-001](../architecture/decisions/ADR-001-account-transaction-consistency.md) for the full analysis, including the corrected detail (found during this review) that the REST call to transaction-service executes *before* the local account-service transaction commits, not after — this extends how long a DB connection is held during the external call, though it does not put the balance mutation's correctness at risk.
- **No cross-service validation on account creation** — `customerId` is accepted without checking customer-service, and there's no database-level foreign key (separate databases by design).
- **`POST /api/v1/transactions` has no access control** — it's "internal" only by convention. Fixing this properly requires service-to-service authentication, out of scope until the auth phase.

## Important architectural decisions

- **ADR-001** (new, this review): documents the account→transaction consistency trade-off formally, including Future Design options (transactional outbox, Kafka, idempotent consumers, event-driven architecture) — none implemented yet, by instruction.
- Decided **not** to add cross-service customer-existence validation to account-service during this review, even though it's a gap, because it would introduce new synchronous inter-service coupling beyond what a "fix a defect" pass should do — it's recorded as a known gap instead, for a deliberate future decision.
- Decided **not** to restructure the account→transaction call to happen strictly after the local transaction commits (e.g. via `@TransactionalEventListener(phase = AFTER_COMMIT)`), even though the review found the call currently executes before commit — this is exactly the kind of change ADR-001 exists to weigh deliberately rather than fix incidentally during a review pass. Added timeouts instead, which bounds the immediate operational risk without changing the transactional structure.

## What was learned

- **Passing `curl` tests does not mean the frontend can reach the API.** CORS is enforced by browsers, not HTTP clients, so a fully "verified" API layer can still be completely unreachable from the one client that matters. This is now called out explicitly in `docs/api/README.md` as something to check whenever a new frontend origin is introduced (e.g. a different port, or a deployed URL).
- **"Newest first" needs an explicit `ORDER BY`.** Relying on incidental database return order (which can look correct by coincidence on a small table) is not the same as a guaranteed order, and it silently breaks pagination correctness (page 2 can repeat or skip rows) as soon as it's exercised for real.
- **A DTO shaped like a domain concept doesn't mean every code path through that concept is consistent.** `deposit()`, `withdraw()`, and `createAccount()`'s `initialDeposit` are all "money entering an account," but only two of the three recorded a ledger entry until this review.

## Next phase

Per [docs/00-project-overview/roadmap.md](../00-project-overview/roadmap.md): **Phase 4 — Authentication**, which should also close the `POST /api/v1/transactions` access-control gap noted above. Phase 2 (in this review's numbering — the broader microservices phase adding payment/beneficiary/card/loan/notification/audit/api-gateway) was **not** started, per explicit instruction for this review.
