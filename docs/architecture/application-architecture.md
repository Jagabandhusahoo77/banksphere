# Application Architecture

_Status: Phase 2A/2B — professional banking UI added on top of the Phase 1 backend, which is unchanged. This document reflects what actually exists today, not the full target architecture (see [overview.md](overview.md) and [microservices.md](microservices.md) for the long-term plan). Frontend-specific detail lives in [docs/frontend/](../frontend/) — this file keeps the system-level view._

## Scope

Phase 1 implemented three independently deployable Spring Boot services, each owning its own PostgreSQL database. Phase 2A/2B added a public marketing site, a redesigned authenticated banking UI, and a full BankSphere design system on top of that same backend — **no backend code changed in Phase 2**. No API Gateway, authentication, messaging (Kafka), or caching (Redis) exists yet — those are introduced in later phases.

```text
                        ┌──────────────────────────────┐
                        │       React Frontend          │
                        │  Public site (Home/About/     │
                        │  Contact) + Internet banking   │
                        │  (Dashboard/Accounts/          │
                        │  Transactions/...)             │
                        └──────────────┬────────────────┘
                                   │ HTTPS/JSON (Axios)
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                     ▼
    ┌──────────────────┐ ┌──────────────────┐ ┌────────────────────┐
    │ customer-service  │ │ account-service   │ │ transaction-service │
    │   :8081            │ │   :8082           │ │   :8083              │
    └─────────┬─────────┘ └─────────┬─────────┘ └──────────┬──────────┘
              │                     │  (2) record tx        │
              │                     └───────────────────────►
              ▼                     ▼                        ▼
    ┌──────────────────┐ ┌──────────────────┐ ┌────────────────────┐
    │ banksphere_customer│ │ banksphere_account│ │ banksphere_transaction│
    │   (PostgreSQL)      │ │   (PostgreSQL)     │ │   (PostgreSQL)         │
    └──────────────────┘ └──────────────────┘ └────────────────────┘
```

All three databases live in a single local PostgreSQL instance for developer convenience (see [database structure](../database/README.md) and the root `backend/README.md`), but each service only ever connects to and migrates its own database — there is no cross-service schema access. In a real deployment each would be its own RDS instance.

## Request flow examples

**Viewing the dashboard**
1. Browser calls `GET /api/v1/customers/{id}` on customer-service to load the profile.
2. Browser calls `GET /api/v1/accounts/customer/{customerId}` on account-service to load accounts.
3. Browser calls `GET /api/v1/transactions/account/{accountId}?page=0&size=5` on transaction-service for recent activity.

Each call is independent; the frontend composes the view client-side. There is no BFF/gateway yet.

**Depositing or withdrawing money (or opening an account with an initial deposit)**
1. Browser calls `POST /api/v1/accounts/{id}/deposit` (or `/withdraw`, or `POST /api/v1/accounts` with a positive `initialDeposit`) on account-service.
2. account-service validates the account is `ACTIVE` and, for withdrawals, that funds are sufficient, then updates the balance — still inside the same local `@Transactional` method (optimistic locking via a `version` column protects against concurrent updates).
3. Still inside that same local transaction (not yet committed), account-service makes a synchronous REST call to transaction-service (`POST /api/v1/transactions`, 3s connect / 5s read timeout) to append a ledger entry. The call is wrapped so any failure (timeout, connection refused, non-2xx) is logged and swallowed rather than propagated — it never rolls back the balance change.
4. The method returns and Spring commits the local (account) database transaction.

**Known limitation:** step 3 is best-effort, not a distributed transaction, and — as described above — it currently executes *before* the local transaction commits rather than after, which holds a DB connection open for the duration of the external call. If transaction-service is unreachable or slow, the balance change still succeeds; only the ledger entry is missing (bounded now by the timeout, previously unbounded). See [ADR-001](decisions/ADR-001-account-transaction-consistency.md) for the full analysis, why this is accepted for Phase 1, and the future options (transactional outbox, Kafka, idempotent consumers) — none of which are implemented yet.

## Service responsibilities (Phase 1)

| Service | Owns | Does not do |
|---|---|---|
| `customer-service` | Customer identity/profile CRUD (create, get, update) | Authentication, accounts, transactions |
| `account-service` | Accounts, balances, deposits, withdrawals | Customer data, transaction history storage |
| `transaction-service` | Transaction ledger, transaction history with pagination | Mutating account balances |

## Data ownership

Each service's schema is versioned independently via its own Flyway migration history in `src/main/resources/db/migration`. No service reads another service's tables directly — all cross-service data access goes through REST APIs (see [Step 12 / API endpoints](../../backend/README.md)).

## Communication

- **Frontend → services**: synchronous REST/JSON over HTTP, via Axios (`frontend/src/services/`). Each service is a separate origin from the frontend (different port), so each explicitly allows cross-origin requests via a `WebMvcConfigurer` CORS mapping on `/api/**` (`banksphere.cors.allowed-origins` / env var `CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`) — added in the Phase 1 review after finding no CORS configuration existed, which would have silently blocked every browser call to the API despite direct HTTP tests (curl) working fine.
- **account-service → transaction-service**: synchronous REST/JSON via Spring's `RestClient`, base URL from `TRANSACTION_SERVICE_URL`, with explicit connect/read timeouts (see above).
- No asynchronous/event-driven communication (Kafka) exists in this phase.

## Known gaps accepted for Phase 1

These are deliberate simplifications, documented here so they're not mistaken for oversights in a future review:

- **No cross-service validation on account creation.** `POST /api/v1/accounts` accepts any `customerId` UUID without checking it against customer-service. There is also no database-level foreign key (separate databases). See [docs/api/README.md](../api/README.md).
- **`POST /api/v1/transactions` has no access control.** Any caller that can reach transaction-service on the network can insert arbitrary ledger entries — it is "internal" only by convention, not enforcement. Fixing this properly means service-to-service authentication, which is out of scope until the auth phase.
- **Account → transaction consistency is best-effort.** See [ADR-001](decisions/ADR-001-account-transaction-consistency.md).

## Frontend (Phase 2A/2B)

The React app is now two shells sharing one codebase: a public marketing site (`/`, `/about`, `/contact`) and an authenticated internet-banking app (`/dashboard`, `/accounts`, `/accounts/:id`, `/transactions`, plus six "coming soon" feature routes). `/` is a real homepage now, not a redirect to `/dashboard`. Full detail — design tokens, component library, routing, responsive/accessibility approach — lives in [docs/frontend/](../frontend/):

- [docs/frontend/design-system.md](../frontend/design-system.md) — branding, color/type/spacing/radius/shadow tokens
- [docs/frontend/architecture.md](../frontend/architecture.md) — layouts, data flow, state management, responsive strategy
- [docs/frontend/routing.md](../frontend/routing.md) — full route table, `ProtectedRoute` behavior, 404 handling
- [docs/frontend/components.md](../frontend/components.md) — every reusable component and its purpose

## Explicitly out of scope for Phase 1

API Gateway, auth-service (real authentication/authorization), payment-service, beneficiary-service, card-service, loan-service, notification-service, audit-service, Kafka, Redis, Kubernetes, Terraform/AWS infrastructure, CI/CD, and observability tooling. See the root [README](../../README.md) for the full phased plan.
