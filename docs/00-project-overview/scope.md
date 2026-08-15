# Scope

## What BankSphere is

A fictional, cloud-native banking platform built incrementally to demonstrate modern software engineering, DevOps, cloud, Kubernetes, and SRE practices. See the root [README](../../README.md) for the full vision and the [Roadmap](roadmap.md) for the phased plan.

## Phase 1 scope — backend + functional frontend

**In scope and implemented:**

- Three independent Spring Boot services: `customer-service`, `account-service`, `transaction-service` (Java 21, Spring Boot, Spring Data JPA, Spring Web, Validation, Actuator).
- PostgreSQL, one database per service, schema-versioned with Flyway.
- Customer CRUD (create/get/update), account creation + deposit/withdrawal with `BigDecimal` money handling and optimistic locking, transaction ledger with pagination.
- A React + TypeScript frontend with an Axios-based API service layer.
- CORS configuration so the frontend (a different browser origin) can actually reach the APIs.
- Dockerfiles for all four components (multi-stage builds) and a `docker-compose.yml` for local orchestration.
- JUnit 5 + Mockito unit tests and `@WebMvcTest` controller tests per backend service.
- Documentation: architecture, database schema, API reference, an ADR for the account→transaction consistency trade-off, and this project-overview set.

## Phase 2A/2B scope — professional banking UI

**In scope and implemented (frontend only — no backend code changed):**

- An original BankSphere design system: brand/semantic/surface/ink color tokens, a type scale, a spacing/radius/shadow system, a hand-authored icon set and SVG illustrations, an original logo/favicon — see [docs/frontend/design-system.md](../frontend/design-system.md).
- A public marketing site (`/`, `/about`, `/contact`) with a real homepage — hero, products, digital banking features, security section, promotional section — and a professional footer.
- A redesigned authenticated internet-banking shell: `BankingHeader` + `BankingSidebar` (desktop) / `MobileNavigation` (mobile bottom tabs), fully responsive.
- Rebuilt Dashboard (balance cards, quick actions, recent transactions), Accounts (`AccountCard` grid), a new Account Details page (`/accounts/:id`) with **working** deposit/withdraw forms wired to the existing `accountService`, and a rebuilt Transactions page.
- Six "coming soon" placeholder pages (Cards, Loans, Payments, Investments, Profile, Support) — real routes and navigation entries, honest empty states, no fabricated backend functionality.
- A ~30-component reusable library (`components/common/`, `components/navigation/`, `components/banking/`, `components/forms/`) and three custom data-fetching hooks (`useCustomer`, `useAccounts`, `useTransactions`) replacing three copies of the same fetch/loading/error logic — see [docs/frontend/components.md](../frontend/components.md).
- `ProtectedRoute` now preserves and restores the originally-requested route after login (previously dead code); a real `NotFound` page replaces the old dashboard-redirect catch-all — see [docs/frontend/routing.md](../frontend/routing.md).

**Explicitly out of scope for both phases (do not add until a later phase):**

- Kafka, Redis
- Kubernetes, Helm, EKS
- Terraform, AWS infrastructure (VPC, RDS, ElastiCache, ALB, Route 53, CloudFront, WAF, IAM, KMS, Secrets Manager)
- CI/CD (GitHub Actions), SonarQube, Trivy
- Argo CD / GitOps
- Prometheus, Grafana, OpenTelemetry, OpenSearch, Fluent Bit
- API Gateway, `auth-service` (real authentication/authorization — since implemented within `customer-service`, see [ADR-002](../architecture/decisions/ADR-002-authentication.md))
- `payment-service`, `card-service`, `loan-service`, `notification-service`, `audit-service` (`beneficiary-service` implemented — see Phase 6 scope below)

## Known Phase 1 gaps (accepted, documented, not fixed)

These were identified during the Phase 1 engineering review and are deliberate simplifications, not oversights — see [docs/09-engineering-journal/](../09-engineering-journal/) for the review that found them and [ADR-001](../architecture/decisions/ADR-001-account-transaction-consistency.md) for the most significant one:

- Account → transaction ledger recording is best-effort, not a distributed transaction (ADR-001).
- `POST /api/v1/accounts` does not validate `customerId` against customer-service.
- `POST /api/v1/transactions` has no access control of its own (no auth exists yet in any service).
- No cross-service database foreign keys (deliberate consequence of database-per-service).

## Known Phase 2 gaps (accepted, documented, not built)

See the Phase 2 [engineering journal entry](../09-engineering-journal/) for full detail:

- No real account-creation UI — accounts are still created via the API directly (curl/Postman), same as Phase 1. `accountService.createAccount()` exists but no page calls it.
- No bundled webfont — a curated system-font stack is used instead, documented as a deliberate choice (no external font URL, no fabricated binary asset) in [design-system.md](../frontend/design-system.md).
- No transfer, bill payment, card, loan, or investment backend — those routes render `ComingSoonPage`, never simulated data or a fake-submitting form. The one exception is Contact's message form, which is explicit client-side-only (see the page for its disclosure text) rather than pretending to call a backend that doesn't exist.
- No visual QA in an actual browser was possible in this environment — see the engineering journal's "Problems encountered" section for what was verified instead (TypeScript, production build, a Docker container smoke test, and a static audit for broken links/overflow-prone classes/missing alt text).

## Phase 6 scope — beneficiary-service

**In scope and implemented:**

- A fourth independent Spring Boot service, `beneficiary-service` (Java 21, same stack as the other three: Spring Boot, Spring Data JPA, Spring Web, Validation, Security, Actuator), its own `banksphere_beneficiary` PostgreSQL database, schema-versioned with Flyway.
- Beneficiary CRUD (create/list/get/update-display-fields/deactivate), JWT-authenticated and ownership-scoped to the requesting customer exactly like account-service/transaction-service — see [docs/security/authorization.md](../security/authorization.md) and [ADR-003](../architecture/decisions/ADR-003-beneficiary-service.md).
- Duplicate-beneficiary prevention enforced at both the application layer and the database layer (a partial unique index), closing the concurrent-request race a single-layer check can't.
- Soft-delete (deactivation) lifecycle, matching the status-transition convention already used by `customers`/`accounts` rather than a hard `DELETE`.
- JUnit 5 + Mockito unit tests and `@WebMvcTest` controller tests, following the exact conventions established in Phase 3A (including the `@Import(SecurityConfig.class)` pattern needed for a plain `Authentication` controller parameter to resolve correctly under `@WebMvcTest`).
- Docker/Compose wiring (`docker-compose.yml`, the Postgres init script, `.env.example`) and a live end-to-end smoke test against the running stack.

**Explicitly out of scope for this phase (do not add until a later phase):**

- Money transfer/payment initiation of any kind — this service only manages beneficiary *records*, never moves money. See [ADR-003](../architecture/decisions/ADR-003-beneficiary-service.md).
- Kafka, a payment switch, interbank transfer rails, notification-service integration.
- Frontend integration — no `beneficiaryService.ts`, no UI. This phase is backend-only.
- `payment-service`, `card-service`, `loan-service`, `notification-service`, `audit-service`, `api-gateway` — still not started.

## Phase 7A scope — internal account-to-account transfer

**In scope and implemented:**

- `POST /api/v1/accounts/transfer` in `account-service`: an atomic, single-database internal transfer between two accounts, JWT-authenticated, source-ownership-enforced (destination may belong to any customer) — see [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md).
- Deterministic account-ordering (deadlock avoidance) and continued use of the existing `Account.version` optimistic-locking column (no pessimistic locking introduced).
- Two real-PostgreSQL integration tests (`AccountTransferRollbackIT`, `AccountTransferConcurrencyIT`, run via `maven-failsafe-plugin` during `mvn verify`) proving real transaction rollback and real concurrent-modification safety — not just Mockito approximations.
- `TRANSFER`-type best-effort ledger legs recorded in transaction-service, consistent with the existing ADR-001 best-effort pattern — no schema change to transaction-service.

**Explicitly out of scope for this phase (do not add until a later phase):**

- A separate Transfer Service, Kafka event publishing, an Outbox pattern, a payment switch, interbank transfer, notification-service integration on transfer completion.
- Any frontend transfer UI — this phase is backend-only (the frontend's existing "Transfer (Coming Soon)" page, from Phase 3A, is untouched).
- Cross-currency conversion — a currency mismatch between source and destination is a hard `422` reject, not a feature gap to fill later with the same request shape.

## Phase 8 scope — frontend banking portal integration

**In scope and implemented:**

- Connected the existing authenticated frontend to the real backend it already had endpoints for: accounts (already done pre-Phase-8), Phase 7A's `POST /api/v1/accounts/transfer` (new `Transfer` page, replacing its `ComingSoonPage`), and beneficiary-service (new `Beneficiaries` page and route — beneficiary-service had zero frontend integration before this phase).
- No fake data anywhere: every balance, transaction, beneficiary, and transfer result on an authenticated screen comes from a real backend response. A genuinely empty transaction list still renders "No transactions yet" — that state was already real (backed by an actual API call), not fixed as a bug.
- Extended `apiClient.ts`'s error handling to preserve HTTP status/details (`utils/apiError.ts`) so money-moving screens can show consistent, contextual copy for 400/401/403/404/409/422/500/network failures instead of only ever the backend's raw top-level message.
- Added a minimal Vitest + React Testing Library test suite (none existed before) covering account/transaction loading and error states, deposit/withdrawal validation and submission, transfer validation/submission/double-submit-prevention/post-success refresh, and beneficiary creation/loading/validation/conflict-handling.
- Fixed a real, pre-existing latent bug in the shared `Modal` component (an unstable `onClose` dependency caused it to steal focus back out of any input on every keystroke) — found because this phase was the first to actually type into a form inside a `Modal`.

**Explicitly out of scope for this phase (do not add until a later phase):**

- Kafka, an Outbox pattern, a payment switch, Kubernetes — unchanged from every prior phase's scope boundary.
- Resolving a beneficiary's saved account number/IFSC to a real BankSphere `destinationAccountId` — no backend endpoint exists to do this (deliberately: an accountNumber-based account lookup would enable account enumeration), so the Transfer page's "another BankSphere account" path requires the sender to enter the recipient's real Account ID directly rather than pretending beneficiary selection resolves one. See [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md) and the Phase 8 engineering journal entry for the full reasoning and the recommended backend follow-up.
- Redesigning the backend in any way — this phase only ever calls existing, already-tested endpoints.

## Phase 8A scope — account identity (IFSC) + deposit workflow fix

**In scope and implemented:**

- `Account.ifsc` added (entity, `V2__add_ifsc_to_accounts.sql`, `AccountResponse`) — a single server-controlled constant (`BANK0000001`) on every account, since BankSphere has no branch model. Never client-suppliable, never randomized per account.
- Account-number generation (already server-side, unique, 12-digit since Phase 1) verified, not rewritten — new tests added to prove it rather than just assert it by convention.
- A real account-creation UI (`Accounts.tsx`, modal-based) — the actual gap behind the reported "deposit not working from the portal": there was no way to open an account through the portal at all (`accountService.createAccount` existed but had zero UI callers), so a real user following the natural journey could never reach a working deposit form. The deposit code path itself, traced end-to-end, was already correct — see the Phase 8A engineering journal entry for the full root-cause trace.
- `AccountDetails.tsx` now displays "BankSphere" / Account Number (full, copyable) / IFSC / Account Type / Available Balance / Status, per this phase's requested layout.
- A real, pre-existing frontend bug fixed: `types/account.ts`'s `AccountCreateRequest` still had a stale `customerId` field left over from before Phase 3A removed it backend-side — harmless in practice (backend ignores it) but a real drift between the type and the actual contract, closed here.

**Explicitly out of scope for this phase (do not add until a later phase):**

- Kafka, Outbox, Payment Switch, Kubernetes, Transfer Service, Notification Service — unchanged from every prior phase's boundary.
- A real branch model for BankSphere — `ifsc` is a single constant by design; multi-branch support is explicitly deferred (see `AccountServiceImpl.BANKSPHERE_IFSC`'s own doc comment).
- Any other frontend redesign — only the Accounts page (new creation modal) and AccountDetails page (identity display) were touched.

## Phase 8B scope — recipient resolution and transfer UX

**In scope and implemented:**

- Closed the Phase 8 gap noted above: `POST /api/v1/accounts/transfer` now identifies the destination by `destinationAccountNumber` + `destinationIfsc` instead of an internal `destinationAccountId` UUID — the internal account id is no longer part of this API, request or response, at all.
- A new `POST /api/v1/accounts/resolve-recipient` endpoint lets the frontend verify a recipient (matching real bank "verify payee" UX) before committing to a transfer. Its response is deliberately minimal (`accountNumber`, `ifsc`, `bankName` — no id, no `customerId`, no balance), keeping the account-enumeration surface to "does this exact account number + IFSC pair exist and is it active," matching how real bank payee-verification APIs behave.
- The Transfer page's recipient step was rebuilt around three real, backend-verified paths: the caller's own other accounts (resolved locally), a saved `ACTIVE` beneficiary (auto-resolved and re-verified against `resolve-recipient`, so a beneficiary is never a bypass of verification), and manual account-number + IFSC entry with an explicit "Verify recipient" step. The old "paste the recipient's raw Account ID" field is gone.
- Same-bank-only enforced explicitly: any IFSC other than `BANK0000001` is rejected with a `422` before any account lookup, distinct from a `404` for a nonexistent-but-correctly-formatted BankSphere account.
- Full design rationale in [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md).
- No database migration needed — `resolveDestinationAccount` is pure query logic (`AccountRepository.findByAccountNumber`, already-backfilled `ifsc` column from Phase 8A).
- Backend: 64 tests (28 controller + 34 service + 2 real-Postgres integration), all passing on `mvn clean verify`. Frontend: 37 tests, all passing on `npm test`; clean `npm run build`. Verified live with real-browser (Puppeteer against the actual system Chrome) end-to-end runs: a manual account-number+IFSC transfer and a saved-beneficiary transfer, each checked for correct balance/ledger updates on both sides and confirming the UUID is never shown to the user, plus explicit security checks (same-account transfer rejected, nonexistent recipient fails safely, `resolve-recipient`'s response carries no enumeration-useful data).

**Explicitly out of scope for this phase (do not add until a later phase):**

- Kafka, Outbox, Payment Switch, Kubernetes, Transfer Service, Notification Service — unchanged from every prior phase's boundary.
- Any interbank/external-bank transfer capability (NEFT/RTGS/IMPS simulation) — BankSphere is a single fictional bank; a non-BankSphere IFSC is cleanly rejected, not stubbed toward future support.
- Rate limiting on `resolve-recipient` — recorded as accepted future hardening in ADR-005, not silently skipped.
- Resolving a real customer display name for the recipient preview — would require a new account-service → customer-service dependency not introduced this phase; the preview only ever shows what account-service itself knows (masked account number, IFSC, "BankSphere") plus, for saved beneficiaries only, the sender's own saved label.

## Phase 9A scope — employee identity and RBAC foundation

**In scope and implemented:**

- A new, separate backend service, `employee-service` (port 8085, `banksphere_employee` database), owning employee identity and authorization metadata only — no customer/account/transaction data duplicated, no direct database access from employee-service to any other service's schema. See [docs/architecture/employee-platform.md](../architecture/employee-platform.md) and [ADR-006](../architecture/decisions/ADR-006-employee-identity-and-rbac.md).
- `Employee` (unique `employeeNumber`/`username`, BCrypt `passwordHash`, `ACTIVE`/`INACTIVE`/`LOCKED` status, many-to-many `Role`s) and a minimal `Branch` model.
- Fixed `Role` (`TELLER`, `KYC_OFFICER`, `LOAN_OFFICER`, `CARD_OFFICER`, `OPERATIONS`, `BRANCH_MANAGER`, `ADMIN`) and `Permission` enums, with a documented, code-defined role→permission mapping (`security/RolePermissions`).
- Employee authentication (`POST /api/v1/employees/auth/login`, `GET /api/v1/employees/me`) using a JWT signed with a **separate signing key** (`EMPLOYEE_JWT_SECRET`) from customer-service's `JWT_SECRET` — a customer token cannot authenticate against employee-service, proven both by a real-key unit test and live via `curl` against a running instance (a real customer-service-issued JWT presented to employee-service returned a genuine `401`).
- Server-side RBAC via Spring Security method security (`@PreAuthorize`) on the admin-only employee-provisioning endpoints (`POST/GET /api/v1/employees`, `GET/PUT /api/v1/employees/{id}[/status]`) — never enforced by hiding a UI element.
- No public employee self-registration; a single seeded bootstrap `ADMIN` employee closes the resulting chicken-and-egg problem (documented local-dev-only credential, same convention as the project's existing default database credentials).
- Structured, audit-ready logging of every login attempt and employee-management action, plus a request-correlation-id filter — not the full Audit Service, explicitly deferred.
- A new, separate frontend application, `employee-portal/` (port 5174) — Login, an authenticated shell, permission-aware navigation, an employee profile page, logout, and an unauthorized page. No operational screens.
- 57 new backend tests (including a parameterized RBAC test proving every non-`ADMIN` role is rejected from admin endpoints, and a real-key cross-service-rejection test) and 15 new frontend tests, all passing; zero regressions in the four existing backend services (134 tests) or the existing customer frontend (37 tests).

**Explicitly out of scope for this phase (do not add until a later phase):**

- Kafka, an Outbox pattern, a Payment Switch, Kubernetes, a Transfer Service, a Notification Service, a real Audit Service — unchanged from every prior phase's boundary.
- Any operational employee-facing capability: KYC review/approval, loan review/approval, card review/approval, cash deposit/withdrawal, service-request processing, transaction investigation, an ATM system. This phase is identity and RBAC only.
- Database-backed, admin-editable role→permission mapping — `ROLE_MANAGE` exists as a permission name for this future capability, but the mapping itself is code-defined for now (see ADR-006, Decision 5).
- Branch-scoped authorization enforcement — the branch id flows through the JWT/profile today, but no endpoint filters by it yet (see ADR-006, Decision 8).
- Any modification to the existing customer-facing services or frontend beyond what employee-service's own Docker/network wiring required (none — `customer-service`/`account-service`/`transaction-service`/`beneficiary-service`/`frontend` were not touched this phase).

## Phase 9B scope — branch cash deposit

**In scope and implemented:**

- The first real employee-initiated banking operation: branch cash deposit. A `TELLER`/`BRANCH_MANAGER` can search for a customer (by account number or customer id), select one of their accounts, and credit it with cash — a real balance mutation in account-service and a real `DEPOSIT` ledger entry in transaction-service, immediately visible through the existing, unmodified customer portal.
- A new "Employee Operations API" in employee-service (`GET /api/v1/operations/customer-search`, `POST /api/v1/operations/cash-deposits`, `GET /api/v1/operations/cash-deposits/history`), orchestrating calls to account-service/customer-service by forwarding the acting employee's own bearer token to each — never a shared internal secret, never asserted identity fields.
- New employee-only endpoints in account-service (`GET /api/v1/accounts/employee-lookup[/customer/{customerId}]`, `POST /api/v1/accounts/{id}/employee-deposit`) and customer-service (`GET /api/v1/customers/employee-lookup/{id}`) — account-service/transaction-service/customer-service each gained their own `EmployeeJwtValidator` to independently recognize employee-service-issued tokens (own key, `EMPLOYEE_JWT_SECRET`) — every existing customer-facing endpoint's code is unchanged.
- Real branch-scoped authorization: a `TELLER` may only credit an account whose IFSC matches their own branch's — derived from the existing `accounts.ifsc` (Phase 8A) and `branches.ifsc` (Phase 9A) fields rather than inventing a new one; `BRANCH_MANAGER`/`ADMIN` are explicitly exempted. See [ADR-007](../architecture/decisions/ADR-007-branch-cash-deposit.md).
- CASH_DEPOSIT permission enforcement reused exactly as Phase 9A defined it (`TELLER`/`BRANCH_MANAGER` only — `ADMIN` deliberately excluded, per ADR-006) — the role→permission mapping itself was not modified.
- A new, minimal `cash_deposit_operations` table in employee-service, storing only immutable operational facts (never balance or customer name, both always re-fetched live) — see ADR-007, Decision 9.
- Employee-portal UI: Cash Operations → Cash Deposit (a 5-step wizard: find customer, select account, amount, review, confirm) and Cash Deposit History.
- 74 new/changed backend tests across 4 services, 9 new frontend tests, zero regressions (all Phase 9A/8B/8/7A/6/1 tests still pass). Verified live end-to-end via `curl` (including the full 401-vs-403 distinction for a customer token hitting an employee-only endpoint) and via a real-browser Puppeteer run driving both portals in the same session (22/22 checks passed).

**Explicitly out of scope for this phase (do not add until a later phase):**

- KYC, loans, cards, ATM, Kafka, Outbox, Payment Switch, Notification Service, a full Audit Service, Kubernetes, external bank transfers, UPI/IMPS/NEFT — unchanged from every prior phase's boundary.
- Cash withdrawal by an employee — only deposit was in scope this phase.
- A second branch/IFSC — BankSphere still has exactly one; the branch-scope check is real but trivially satisfied today (see ADR-007, Decision 6).
- Database-backed, admin-editable role→permission mapping (`ROLE_MANAGE` still unused beyond its name — unchanged from Phase 9A).
- A dedicated fix for the "employee token hits an unmodified customer-only endpoint" rough edge (currently a `400`, not a clean `403`, on a request that never leaks any data) — documented as an accepted limitation in ADR-007 rather than silently left undiscovered.

## Phase 9C scope — Customer 360 + KYC + document verification

**In scope and implemented:**

- A sixth backend service, `kyc-service` (port 8086, `banksphere_kyc` database) — KYC applications, document metadata/verification state, and review decisions. Owns its own database; no cross-database foreign keys; no code shared with any other service. See [ADR-008](../architecture/decisions/ADR-008-kyc-domain-and-document-storage.md) and [docs/architecture/customer-360-and-kyc.md](../architecture/customer-360-and-kyc.md).
- A full customer KYC application flow: create → upload PAN/Identity Proof/Address Proof/Bank Statement → submit → (if requested) resubmit, with a locked-in state machine (`DRAFT → SUBMITTED → UNDER_REVIEW → {APPROVED, REJECTED, ADDITIONAL_INFORMATION_REQUIRED → RESUBMITTED → UNDER_REVIEW}`) enforced by one authoritative `KycStateMachine` table — every out-of-table transition rejected with `422`.
- A full employee KYC review flow: queue → start-review → verify/reject each document → approve/reject (or request additional information), each individually `@PreAuthorize`-gated (`KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE`/`KYC_REJECT` — only `KYC_REJECT` was newly added to the Phase 9A catalog; the other three already existed, unused).
- A `DocumentStorage` interface + `LocalDocumentStorage` (filesystem-backed, a Docker named volume) — the KYC domain has zero direct dependency on AWS SDK classes; a future `S3DocumentStorage` is a drop-in replacement. No AWS/S3 introduced this phase.
- Real optimistic-locking concurrency protection (`KycApplication.version`) proven by a real-Postgres integration test (`KycApplicationReviewConcurrencyIT`) — a genuine bug (an audit line falsely claiming `SUCCESS` a moment before the database rejected the same write) was found and fixed during this test's own development, by switching every mutation to `saveAndFlush`.
- Customer 360: a new `GET /api/v1/employee/customers/{customerId}/360` in employee-service, aggregating customer-service/account-service/transaction-service/beneficiary-service/kyc-service live (no data copied into employee-service's own database) with **section-level graceful degradation** — a caller missing a section's permission gets `available: false` with a reason, never a whole-endpoint `403` and never fabricated data. New employee-only endpoints feeding it: `GET /api/v1/customers/employee-lookup/{id}/profile` (customer-service, additive), `GET /api/v1/transactions/employee/account/{accountId}` (transaction-service, new), `GET /api/v1/beneficiaries/employee/customer/{customerId}` (beneficiary-service, new — this service's first-ever employee-token acceptance), `GET /api/v1/kyc/employee/customer/{customerId}` (kyc-service).
- Customer portal: a new "KYC" nav item and page (status view, start-application form, document upload via a new `FileUpload` component, submit/resubmit).
- Employee portal: "Customer 360" (search by account number → consolidated view) and "KYC & Compliance" (Queue, Document Verification, Completed Reviews, Review Application) — the portal's first nested-nav module.
- 66 new kyc-service tests, plus new/changed tests in customer-service, transaction-service, beneficiary-service, and employee-service — 348 backend tests passing across all six services, zero regressions. Both frontends build clean with zero regressions in existing tests (37 customer-portal, 24 employee-portal). Verified live end-to-end via `curl` (full application lifecycle, cross-customer denial, wrong-role denial, missing-document rejection, invalid-transition rejection, duplicate-application conflict) and via a real-browser Puppeteer run driving both portals through the complete customer-submits → employee-reviews-and-approves → customer-sees-status journey.

**Explicitly out of scope for this phase (do not add until a later phase):**

- Loans, cards, forex, cash withdrawal, ATM, Notification Service, promotional campaigns — unchanged from every prior phase's boundary. Customer 360 explicitly reports these as `unavailableCapabilities`, never fabricated data.
- AI-assisted document verification — documented as a future, advisory-only (never autonomous) extension point; no AI implemented this phase.
- Kafka, an Outbox pattern, a real Audit Service — `KycAuditLog`'s structured log lines are shaped to be adoptable by a future pipeline, but none is built.
- S3-backed document storage — the `DocumentStorage` interface makes this a future drop-in, not built this phase.
- A maker-checker (dual-approval) model beyond the optimistic-locking conflict protection already built.
- Branch-scoped KYC — a deliberate decision, not an oversight: unlike `Account.ifsc` for cash deposit, a KYC application has no natural branch anchor, so none was invented (see ADR-008, Decision 11).
- A re-KYC flow for a customer with a prior terminal (`APPROVED`/`REJECTED`) application.

## Phase 9D scope — customer OTP authentication + step-up authentication

**In scope and implemented:**

- Customer OTP login, additive to (never replacing) the existing password login: `POST /api/v1/auth/otp/request` (email or phone → generic response + `challengeId`, never reveals whether the identifier is registered) and `POST /api/v1/auth/otp/verify` (`{challengeId, otp}` → the same `AuthResponse` shape password login already returns). Both `/login` and `/otp/verify` now also issue an HttpOnly, `SameSite=Lax` refresh-token cookie.
- Refresh tokens: opaque, SHA-256-hashed at rest, rotated on every use, with reuse detection that revokes an entire token family — `POST /api/v1/auth/token/refresh`, `POST /api/v1/auth/logout` (now also revokes + clears the cookie). See [ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md), Decision 11, including a real reuse-detection bug found via live Docker verification and fixed before shipping.
- Step-up authentication for transfers at or above a configurable threshold (default ₹50,000, illustrative): `POST /api/v1/auth/step-up/{request,verify}` (customer-service) and `POST /api/v1/auth/step-up/confirm` (called by account-service, forwarding the customer's own bearer token). A step-up challenge is bound to the exact operation (source account, destination, amount, currency) via a SHA-256 context hash — amount/recipient tampering after verification is rejected with `403`, never silently allowed. See ADR-009, Decisions 12-13.
- `POST /api/v1/accounts/transfer` integration: unchanged core transfer logic (ADR-004), now gated by `StepUpPolicy` and wrapped with an optional idempotency mechanism (`idempotencyKey`, enforced by a real database unique constraint) so a double-click/network-retry/browser-refresh can never execute the same transfer twice. See ADR-009, Decisions 14-15.
- A local-development-only `GET /api/v1/auth/dev/otp-inbox`, whose controller bean does not exist in the application context at all unless explicitly enabled — verified directly via an `ApplicationContextRunner` test, not just asserted.
- Customer portal: a "Password" / "One-time code" tab on the existing Login page; a reusable `StepUpOtpModal` wired into the Transfer flow; a dev-only OTP inbox panel (`import.meta.env.DEV`-gated) reused in both places.
- 118 new/changed customer-service tests, 113 new/changed account-service tests (both including the new OTP/step-up/idempotency domains), zero regressions across employee-service (93), transaction-service (20), beneficiary-service (30), kyc-service (65) — 439 backend tests total. Both frontends build clean; customer frontend gained 6 new tests (Login OTP flows, Transfer step-up flow), zero regressions (43 total); employee-portal unchanged (24 tests, untouched this phase). Verified live end-to-end via `curl` against rebuilt Docker containers with direct database inspection — every one of the phase's five required flows (login, invalid OTP, transfer step-up, replay, tampering) plus idempotency and refresh-token rotation/reuse-detection/logout.

**Explicitly out of scope for this phase (do not add until a later phase):**

- Cash withdrawal, payments, service requests, loans, cards, forex, international transfers, notifications, promotional campaigns — unchanged from every prior phase's boundary. `StepUpPolicy.requiresStepUpForWithdrawal()`/`requiresStepUpForBeneficiaryCreation()` exist as real, tested policy methods but are deliberately not wired to any endpoint, since none of those endpoints exist yet.
- A dedicated identity/auth microservice — OTP and refresh-token logic live in customer-service, extending its existing authentication ownership rather than splitting it (see ADR-009, Decision 1).
- Real SMS/Email/WhatsApp delivery, and the Notification Service such a provider might eventually route through — `OtpDeliveryProvider`/`MockOtpDeliveryProvider` only.
- Distributed rate limiting — the OTP rate limiter is in-memory, single-JVM-instance, explicitly documented as not production-ready (ADR-009, Decision 6); Redis remains out of scope until its own designated phase.
- Authenticator-app TOTP or any other second-factor type beyond OTP; adaptive/risk-based/AI-driven step-up policy — documented as future extension points only (ADR-009, Decision 17).
- A real browser (Puppeteer/Playwright) E2E run — not performed this phase (no such tooling installed in this environment); the five required flows were instead verified via direct `curl` calls against live, rebuilt Docker containers with database-state inspection, plus Vitest component tests simulating the same user interactions against a mocked API layer. See the Phase 9D engineering journal entry for the honest breakdown of what was and wasn't exercised.

## Non-negotiable constraints (apply to every phase)

- Fictional project only — no real bank's code, branding, APIs, credentials, or proprietary assets. See [CLAUDE.md](../../CLAUDE.md) for the permanent development rules that encode this and other constraints.
- Never hard-code real secrets or commit them to Git.
- Money is always `BigDecimal` / `NUMERIC`, never floating point.
