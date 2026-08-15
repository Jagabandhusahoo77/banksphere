# Roadmap

BankSphere is built incrementally. This list mirrors the root [README](../../README.md)'s development phases, with status added as phases complete. Do not start a phase's work before the previous phase is marked done, and do not pull technology forward from a later phase (see [scope.md](scope.md)).

| # | Phase | Status |
|---|---|---|
| 1 | Project structure | Done |
| 2 | Frontend + backend foundation (customer/account/transaction services, React app) | **Done — reviewed** |
| 3 | PostgreSQL | **Done — reviewed** (folded into Phase 2's implementation) |
| 4 | Authentication | **Done — reviewed** (registration/login/logout, JWT, ownership enforcement; folded into "Phase 3A" in the engineering journal — money transfer itself is a separate follow-on, "Phase 3B") |
| 5 | Account + transaction functionality | **Done — reviewed** (folded into Phase 2's implementation) |
| 6 | Microservices (remaining services: payment, beneficiary, card, loan, notification, audit, api-gateway, auth) | **In progress** — `beneficiary-service` done; payment/card/loan/notification/audit/api-gateway not started |
| 7 | Kafka + Redis | Not started |
| 8 | Docker | **Done — reviewed** (Dockerfiles + docker-compose folded into Phase 2's implementation) |
| 9 | Kubernetes | Not started |
| 10 | AWS + Terraform | Not started |
| 11 | CI/CD | Not started |
| 12 | GitOps + Argo CD | Not started |
| 13 | Monitoring, logging, tracing | Not started |
| 14 | SRE testing + failure scenarios | Not started |

**Naming note:** this table's "Phase 2" (row above) is the original roadmap's *Frontend + backend foundation* — what this project's own docs call "Phase 1" (see below). The subsequent professional-banking-UI work (original branding, design system, public site, redesigned internet banking) is referred to in the engineering journal as **"Phase 2A/2B"** and is a frontend-only enhancement on top of that same Phase 1 backend — it doesn't map to a numbered row in this table, since the original roadmap didn't anticipate a dedicated design pass. It's tracked in [scope.md](scope.md) and the [2026-08-11 Phase 2 journal entry](../09-engineering-journal/). This table's row 4, "Authentication," is what the engineering journal calls **"Phase 3A"** — done within `customer-service` rather than the originally-scaffolded empty `auth-service` directory (see [ADR-002, Decision 1](../architecture/decisions/ADR-002-authentication.md#decision-1--authentication-lives-in-customer-service-not-a-new-auth-service) for why). Money transfer between customers, a natural next step now that real ownership enforcement exists, is tracked separately as **"Phase 3B"** and is not started. A same-day, frontend-only public-website redesign (new homepage structure, mega-menu navigation, 8 new public pages) is tracked as **"Phase 3C"** — like "Phase 2A/2B," it doesn't map to a numbered row here, since the original roadmap didn't anticipate a dedicated design pass; see the [2026-08-12 Phase 3C journal entry](../09-engineering-journal/) and [docs/frontend/homepage-design.md](../frontend/homepage-design.md).

## Why Phases 2/3/5/8 collapsed into one delivery

The original phase list separates frontend/backend foundation, PostgreSQL, account/transaction functionality, and Docker into four phases. In practice, a working vertical slice needs all four together to be testable end-to-end (a service with no database is not runnable; a service with no Docker image can't be verified in the target packaging). They were implemented and reviewed as a single unit, referred to as "Phase 1" in this project's own documentation (see [scope.md](scope.md) and the engineering journal), while still respecting every phase's individual constraints — e.g. no authentication was added even though Phase 4 comes after this work.

## What Phase 4 (Authentication / "Phase 3A") actually built

Completed 2026-08-12 — see the [Phase 3A engineering journal entry](../09-engineering-journal/) and [docs/security/](../security/) for full detail:

- Real registration/login/logout in `customer-service` (not a new `auth-service`), BCrypt password hashing, JWT (HS256, shared secret) sessions.
- Ownership enforcement on every existing protected endpoint across all three services — closing the Phase 1/2 gap where any customer could access any other customer's data by changing an ID.
- Closed the Phase 1 gap where `POST /api/v1/transactions` had no access control (see [scope.md](scope.md) known gaps) — it now requires authentication (though not a full ownership re-check; see [ADR-002, Decision 5](../architecture/decisions/ADR-002-authentication.md#decision-5--post-apiv1transactions-requires-authentication-but-does-not-re-verify-account-ownership)).

**Not built** (explicitly out of scope for this phase, tracked in [docs/security/threat-model.md](../security/threat-model.md)): token revocation, refresh tokens, MFA, password reset, rate limiting, an auth event audit log, and money transfer itself (Phase 3B).

## Phase 9D — customer OTP authentication + step-up authentication

Completed 2026-08-14 — see the [Phase 9D engineering journal entry](../09-engineering-journal/), [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md), and [ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md):

- Customer OTP login (`/otp/request`, `/otp/verify`), additive alongside the existing password login — same `AuthResponse` shape, generic responses that never reveal whether an identifier is registered.
- Refresh tokens: opaque, SHA-256-hashed, HttpOnly-cookie-only, rotated on every use with reuse detection that revokes an entire token family (`/token/refresh`, `/logout`). A real bug in the reuse-detection revocation — silently rolled back by the very exception used to reject the request — was found via live Docker verification (not caught by any mocked unit test) and fixed by extracting the revocation into its own `REQUIRES_NEW`-transaction bean, the same pattern account-service's idempotency bookkeeping already used.
- Step-up authentication for transfers at/above a configurable threshold (`/step-up/request`, `/step-up/verify`, `/step-up/confirm`) — bound to the exact operation via a SHA-256 context hash over its canonical fields, so amount/recipient tampering after verification is rejected, never silently allowed.
- `POST /api/v1/accounts/transfer` gated by the new `StepUpPolicy` and wrapped with an idempotency mechanism enforced by a real database unique constraint — a double-click/retry can never execute the same transfer twice.
- 118 customer-service + 113 account-service tests (new/changed), zero regressions across the other four backend services (439 backend tests total). Both frontends build clean; customer frontend gained 6 new tests, zero regressions. Verified live end-to-end via `curl` against rebuilt Docker containers with direct database-state inspection — all five required flows (login, invalid OTP, transfer step-up, replay, tampering), plus idempotency and refresh-token rotation/reuse-detection/logout.
- No real browser (Puppeteer/Playwright) E2E run this phase — not installed in this environment; documented honestly rather than claimed. `curl`-against-live-containers plus Vitest component tests covered the equivalent ground.
- Explicitly stops here — no cash withdrawal, payments, loans, cards, forex, notifications, Redis, or AI started.

## Phase 9C — Customer 360, KYC, and document verification

Completed 2026-08-14 — see the [Phase 9C engineering journal entry](../09-engineering-journal/), [docs/architecture/customer-360-and-kyc.md](../architecture/customer-360-and-kyc.md), and [ADR-008](../architecture/decisions/ADR-008-kyc-domain-and-document-storage.md):

- A sixth backend service, `kyc-service` (port 8086) — a genuine bounded domain (KYC applications, documents, review decisions) exposing a complete API surface directly to both portals, not proxied through employee-service.
- A full customer KYC lifecycle and a full employee review lifecycle, both funneled through one authoritative state-machine table (`DRAFT → SUBMITTED → UNDER_REVIEW → {APPROVED, REJECTED, ADDITIONAL_INFORMATION_REQUIRED → RESUBMITTED → UNDER_REVIEW}`) — every other transition rejected with `422`.
- A `DocumentStorage` interface + filesystem-backed implementation — zero AWS SDK dependency, a future S3 implementation is a drop-in.
- Customer 360: a new employee-service aggregation endpoint spanning five services live (no data copied), introducing this codebase's first section-level permission-graceful-degradation response shape.
- `KYC_REJECT` added to the existing Phase 9A permission catalog; `KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE` (previously unused) now enforce real endpoints.
- A real concurrency bug (a false-success audit line racing ahead of an optimistic-lock rejection) found and fixed via a real-Postgres integration test.
- 66 new kyc-service tests + changes across four other backend services — 348 backend tests passing, zero regressions. Both frontends build clean, zero regressions. Verified live via `curl` and a real-browser Puppeteer run spanning both portals through the full customer-submits → employee-approves → customer-sees-status journey.
- Explicitly stops here — no loans, cards, forex, AI, Kafka, Notification Service, or Kubernetes started.

## Phase 9B — branch cash deposit

Completed 2026-08-13 — see the [Phase 9B engineering journal entry](../09-engineering-journal/), [docs/architecture/employee-operations.md](../architecture/employee-operations.md), and [ADR-007](../architecture/decisions/ADR-007-branch-cash-deposit.md):

- The first real employee-initiated banking operation: a teller can find a customer by account number, select an account, and credit it with cash — a real account-service balance mutation and a real transaction-service ledger entry, immediately visible in the unmodified customer portal.
- New "Employee Operations API" in employee-service, orchestrating account-service/customer-service calls by forwarding the acting employee's own bearer token — never a shared secret, never trusted request-body identity fields.
- Real branch-scoped authorization, derived from the existing `accounts.ifsc`/`branches.ifsc` fields rather than an invented one — a `TELLER` can only credit their own branch's accounts; `BRANCH_MANAGER`/`ADMIN` are explicitly exempted.
- `CASH_DEPOSIT` permission reused exactly as Phase 9A defined it — the role→permission mapping was not modified.
- Three more services (account/transaction/customer) gained the ability to independently validate employee-service-issued JWTs, alongside — never replacing — their existing customer-token validation. Every existing customer-facing endpoint's code is byte-for-byte unchanged.
- 74 new/changed backend tests + 9 new frontend tests, zero regressions anywhere. Verified live via `curl` and a real-browser Puppeteer run spanning both portals in one session (22/22 checks).
- Explicitly stops here — no KYC, loans, cards, cash withdrawal, ATM, Kafka, or Kubernetes started.

## Phase 9A — employee identity and RBAC foundation

Completed 2026-08-13 — see the [Phase 9A engineering journal entry](../09-engineering-journal/), [docs/architecture/employee-platform.md](../architecture/employee-platform.md), and [ADR-006](../architecture/decisions/ADR-006-employee-identity-and-rbac.md):

- New `employee-service` (port 8085) and new `employee-portal/` frontend (port 5174) — a second, fully separate channel alongside the existing customer portal, sharing only visual design tokens, no runtime state.
- Employee JWTs signed with a separate key (`EMPLOYEE_JWT_SECRET`) from customer JWTs (`JWT_SECRET`) — a real customer session cannot authenticate as an employee, proven live (a real customer-service-issued token presented to employee-service returned `401`), not just asserted.
- Fixed `Role`/`Permission` model with a documented role→permission mapping, enforced server-side via `@PreAuthorize` — found and fixed a real bug along the way (an `AccessDeniedException` silently absorbed by a generic exception handler, turning intended 403s into 500s).
- No public employee registration; one seeded bootstrap admin. Structured audit-ready login/action logging, not yet a full Audit Service.
- 57 new backend tests + 15 new frontend tests, zero regressions anywhere else in the system.
- Explicitly stops here — no KYC, loans, cards, cash operations, ATM, Kafka, or Kubernetes started.

## Phase 8B — recipient resolution and transfer UX

Completed 2026-08-13 — see the [Phase 8B engineering journal entry](../09-engineering-journal/) and [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md):

- Closed the cross-service gap Phase 8 surfaced: `POST /api/v1/accounts/transfer` now identifies its destination by `destinationAccountNumber` + `destinationIfsc`, never an internal UUID — the account id is not part of this API in either direction anymore.
- New `POST /api/v1/accounts/resolve-recipient` endpoint (minimal response: account number, IFSC, bank name only) lets the frontend verify a recipient before committing, without materially increasing account-enumeration risk.
- Transfer page's recipient step rebuilt around three real paths (own accounts / saved beneficiary / manual account number + IFSC), replacing Phase 8's "paste the recipient's raw Account ID" stopgap; a saved beneficiary is always re-verified against the live account, never trusted as a stale snapshot.
- Same-bank-only enforced explicitly (non-`BANK0000001` IFSC rejected with a distinct `422`, separate from a `404` for a nonexistent BankSphere account).
- No database migration required. Backend: 64 tests, `mvn clean verify` clean. Frontend: 37 tests, clean build. Verified with real-browser (Puppeteer) end-to-end runs covering both recipient paths, balance/ledger correctness on both sides, and the security properties (no UUID ever shown, same-account/nonexistent-account rejected safely, no enumeration-useful data in the resolve-recipient response).

## Phase 8A — account identity (IFSC) + deposit workflow fix

Completed 2026-08-13 — see the [Phase 8A engineering journal entry](../09-engineering-journal/):

- Added `Account.ifsc` (single server-controlled constant, `BANK0000001` — no branch model exists) end-to-end: entity, migration, DTO, frontend display.
- Verified (not rewrote) existing account-number generation; added tests proving it rather than asserting it by convention.
- Found and fixed the real root cause behind "deposit not working from the portal": there was no account-creation UI at all, so a real user could never reach a working deposit form. Deposit itself, traced end-to-end with real browser tooling (first time available in this environment), was already correct.
- Built the missing account-creation UI; fixed a separate real bug (a stale `customerId` field in the frontend's `AccountCreateRequest` type).

## Phase 8 — frontend banking portal integration

Completed 2026-08-13 — see the [Phase 8 engineering journal entry](../09-engineering-journal/):

- Real Transfer page (replacing its `ComingSoonPage`) wired to Phase 7A's `POST /api/v1/accounts/transfer`, and a brand-new Beneficiaries page/route wired to beneficiary-service (Phase 6) — the first frontend integration either has ever had.
- Deposit/withdraw (already real since Phase 2A/2B) and Transactions (already real since Phase 2A/2B) verified and lightly hardened, not rebuilt — the "No transactions yet" state reported as suspicious turned out to be a real, correctly-empty API response, not a bug.
- A genuine cross-service gap surfaced and honestly documented rather than papered over: beneficiaries can't yet be resolved to a transferable account id — see [scope.md](scope.md#phase-8-scope--frontend-banking-portal-integration).
- First frontend test suite (Vitest + React Testing Library, 29 tests) and a real pre-existing `Modal` focus-stealing bug fixed along the way.

## Phase 7A — atomic internal account-to-account transfer

Completed 2026-08-13 — see the [Phase 7A engineering journal entry](../09-engineering-journal/) and [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md):

- `POST /api/v1/accounts/transfer` added to `account-service` (not a new Transfer Service — see ADR-004, Decision 1) — atomic, single-database, JWT-authenticated, source-ownership-enforced.
- Deterministic account ordering for deadlock avoidance, continuing to rely on the existing `Account.version` optimistic-locking column rather than introducing pessimistic locking.
- Two real-PostgreSQL integration tests proving genuine transaction rollback and genuine concurrent-modification safety, run via `maven-failsafe-plugin` during `mvn verify` — not Mockito approximations.
- `TRANSFER`-type ledger legs recorded in transaction-service on the same best-effort basis as deposit/withdraw (ADR-001) — no transaction-service schema change.

**Not built in this pass** (explicitly out of scope): a separate Transfer Service, Kafka, an Outbox pattern, a payment switch, interbank transfer, notification-service integration, any frontend transfer UI, cross-currency conversion.

## What Phase 6 (Microservices) has built so far: `beneficiary-service`

Completed 2026-08-13 — see the [Phase 6 engineering journal entry](../09-engineering-journal/), [docs/architecture/decisions/ADR-003-beneficiary-service.md](../architecture/decisions/ADR-003-beneficiary-service.md), and [scope.md](scope.md#phase-6-scope--beneficiary-service) for full detail:

- A fourth independent Spring Boot service, `beneficiary-service` (port 8084), following the exact same stack/conventions as customer-service/account-service/transaction-service — its own `banksphere_beneficiary` database, Flyway-versioned schema, JWT-authenticated and ownership-scoped endpoints.
- Beneficiary CRUD (`POST/GET /api/v1/beneficiaries`, `GET/PUT/DELETE /api/v1/beneficiaries/{id}`) with duplicate prevention enforced at both the application layer and a database-level partial unique index, and a soft-delete (deactivation) lifecycle matching the existing `customers`/`accounts` status-transition convention.
- 25 tests (11 service-layer Mockito, 14 `@WebMvcTest` controller) plus a live Docker smoke test against the running stack.

**Not built in this pass** (explicitly out of scope, tracked for a later Phase 6 sub-step): payment-service, card-service, loan-service, notification-service, audit-service, api-gateway; Kafka; any frontend integration for beneficiaries.

## Immediate next phase: money transfer (Phase 3B)

Phase 7A (above) built the backend atomic-transfer capability this phase needs (`POST /api/v1/accounts/transfer`) — Phase 3B itself, still not started, is the customer-facing side:

- A real Transfer page in the frontend, replacing the existing "Transfer (Coming Soon)" nav entry/route (`/transfer`) from Phase 3A, calling the Phase 7A endpoint.
- Likely needs beneficiary-service (Phase 6) wired in as the "who can I send money to" picker, since that's exactly the gap beneficiary-service was built to fill (see [ADR-003](../architecture/decisions/ADR-003-beneficiary-service.md)).

## Engineering journal

Day-to-day narrative of what was actually done, in what order, and why, lives in [docs/09-engineering-journal/](../09-engineering-journal/) — this roadmap only tracks phase-level status.
