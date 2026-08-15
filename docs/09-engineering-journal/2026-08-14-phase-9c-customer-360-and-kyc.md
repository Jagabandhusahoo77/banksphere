# 2026-08-14 — Phase 9C: Customer 360, KYC, and Document Verification

## Objective

Build a full KYC (Know Your Customer) application/document/review workflow spanning both portals, plus a Customer 360 aggregated view for employees — on top of Phase 9A's identity/RBAC foundation and Phase 9B's employee-operations pattern. Explicitly not loans, cards, forex, AI, Kafka, or a Notification Service. The task's own framing set the central constraint up front: "the employee portal is a CHANNEL, not the owner of every banking domain — do NOT turn employee-service into a monolith."

## Inspection before writing code

Per the recurring project convention (and the task's own explicit 13-point inspection checklist), the existing domain model, RBAC mapping, and JWT patterns were inspected before any design decision:

- `Permission`/`RolePermissions` in employee-service: confirmed `KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE` already existed in the Phase 9A catalog but were never used by any real endpoint. `KYC_REJECT` did not exist. `KYC_OFFICER` held `CUSTOMER_VIEW`/`ACCOUNT_VIEW`/`KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE`; `BRANCH_MANAGER` held `KYC_VIEW`/`KYC_REVIEW` but not `KYC_APPROVE` — an ADR-006 design choice, not an oversight, and left unchanged.
- Cash deposit's employee-service-as-orchestrator pattern (Phase 9B) vs. beneficiary-service's directly-called-by-the-frontend pattern (Phase 6): KYC's shape — a genuine bounded domain with its own data and its own complete API surface for two principal types — matches the latter, not the former. This ruled out routing KYC review through employee-service's existing Operations API.
- `Account`/`Customer` domain models (again, as in Phase 9B): confirmed neither has a branch relationship. This directly informed the branch-scope decision below.
- account-service's Phase 9B `EmployeeJwtAuthenticationFilter`/`EmployeePrincipal` quartet: used as the exact template for kyc-service's own (new) dual-JWT security layer, and for upgrading transaction-service's existing-but-minimal employee-token handling.

## kyc-service: a sixth service, not a sub-module of employee-service

The single most consequential decision this phase: KYC applications, documents, and review state get their own service and their own database (`banksphere_kyc`), following the exact per-service pattern every prior service uses — no shared module, no shared database, no cross-database foreign keys. `customerId`/`reviewedBy`/`changedByEmployeeId` all reference other services' primary keys by UUID value only, the same convention `Beneficiary.customerId` already established.

The second consequential decision, made explicitly rather than by default: kyc-service exposes **two complete API surfaces directly** — `/api/v1/kyc/applications/**` for the customer portal, `/api/v1/kyc/employee/**` for the employee portal — neither proxied through another service. This meant building kyc-service's own dual-JWT security layer from scratch (`JwtValidator`+`EmployeeJwtValidator`, both filters registered alongside each other), the first service in the codebase where *both* principal types were needed for genuinely separate, first-class endpoint groups from day one (not "also accept an employee token on a formerly-customer-only endpoint," as every prior dual-JWT addition had been).

## The KYC state machine: one table, not scattered checks

`KycStateMachine` (a single static `Map<KycStatus, Set<KycStatus>>`) is the one authoritative source every mutation — customer-initiated (`submit`/`resubmit`) and employee-initiated (`start-review`/`request-information`/`approve`/`reject`) alike — funnels through via `requireValidTransition`, which throws `InvalidStateTransitionException` (`422`) for anything outside the locked-in graph. This was modeled directly on `RolePermissions`'s own "one authoritative table, not ad hoc checks" shape from Phase 9A. Resubmission deliberately reuses the identical `start-review` action to move `RESUBMITTED → UNDER_REVIEW` rather than getting a second, near-duplicate endpoint.

## A real concurrency bug found while writing the concurrency proof itself

The task explicitly required proving "Officer B must receive a conflict, never overwrite Officer A's decision" with a real database integration test, following the exact pattern `AccountTransferConcurrencyIT` established in Phase 7A. `KycApplicationReviewConcurrencyIT` seeds an `UNDER_REVIEW` application and races an `approve` against a `reject` from two threads via a `CyclicBarrier`.

The first run against real Postgres passed its own assertions (exactly one decision won, the other got `ObjectOptimisticLockingFailureException`) — but the audit log output told a different story: **both** `KYC_APPROVED` and `KYC_REJECTED` were logged with `result=SUCCESS`, even though only one write actually committed. Root cause: every mutation method called `applicationRepository.save(application)` (not `saveAndFlush`) followed immediately by an audit-log call — with Hibernate's default flush timing, `save()` doesn't necessarily hit the database (and therefore doesn't necessarily trigger the version check) before the audit line is written, so the *losing* transaction's audit line claimed success a moment before the database actually rejected the write on commit.

Fixed by switching every review-decision write (`submit`, `resubmit`, `startReview`, `requestInformation`, `approve`, `reject`) to `saveAndFlush()`, forcing the optimistic-lock conflict to surface synchronously, before any audit call. Re-running the same test after the fix: exactly one audit line appears, for the winning decision only. This was caught by the test's own explicit purpose (proving the concurrency property for real, not by inspection) — a concrete instance of why the task's insistence on a real database test, not a Mockito approximation, mattered.

## Document storage: an interface first, filesystem today

`DocumentStorage` (`store`/`load`, keyed by an opaque `storageReference`) is the KYC domain's only dependency for persisting document bytes — no class outside the `storage` package touches an AWS SDK type or `java.nio.file` directly. `LocalDocumentStorage` writes to a Docker named volume (`KYC_DOCUMENT_STORAGE_PATH`, default `/data/kyc-documents`) using a freshly generated UUID as the on-disk filename, never the customer's own `originalFileName` — closing both a path-traversal vector and a same-name-collision risk in one decision. `storageReference` never appears in any DTO; the one place stored bytes are returned is a dedicated, `KYC_REVIEW`-gated content-streaming endpoint. No AWS/S3 dependency was added — the interface exists specifically so a future `S3DocumentStorage` is a drop-in replacement with zero KYC business-logic change.

## Customer 360: an aggregation, not a data copy — and a new permission model

Customer 360 needs data spanning five services. The rejected approach (copying customer/account/transaction data into employee-service's own database, keeping it in sync) would have violated the task's own central constraint directly. The chosen approach extends employee-service's existing Phase 9B `OperationsController`/`OperationsServiceImpl` aggregation pattern with three new REST clients (`TransactionLookupClient`, `BeneficiaryLookupClient`, `KycLookupClient`) and one new endpoint, `GET /api/v1/employee/customers/{customerId}/360` — every field fetched live, on every request, nothing persisted.

The genuinely new pattern this phase introduces: **section-level graceful degradation**. Every prior permission-gated endpoint in this codebase is all-or-nothing — hold the permission, get the whole response; lack it, get `403`. Customer 360 can't work that way, since a `LOAN_OFFICER` and a `KYC_OFFICER` are each authorized for a different, overlapping subset of the same customer's data. The response is five independent `Customer360Section<T>` values, each `{available, unavailableReason, data}`, decided against the caller's actual permission set (extracted from their JWT authorities at the controller and passed down) rather than a single blanket check. A section the caller can't see is explicitly marked unavailable with a reason — never silently empty (which would look like "no data") and never fabricated. This is documented as a deliberate, new pattern in ADR-008 specifically so a future aggregation endpoint doesn't have to rediscover the reasoning.

## Two `AccessDeniedException`-handler gaps closed before they could bite

Adding `@PreAuthorize` to transaction-service and beneficiary-service for the first time (Customer 360's transactions and beneficiaries sections) meant both services needed the same explicit `AccessDeniedException → 403` handler in their `GlobalExceptionHandler` that ADR-006 found *missing* in employee-service during Phase 9A (where its absence turned every `@PreAuthorize` rejection into an unexplained `500`). Both handlers were added proactively this time, before ever running the new endpoints, rather than being discovered by a failing test.

## transaction-service's employee-token handling: upgraded, not just extended

transaction-service already accepted an employee-signed token since Phase 9B, but only as "a valid JWT" — the principal was a bare, defensively-shaped string with no real authorities, since `POST /api/v1/transactions` never checked *which* permissions a token carried. Customer 360's transactions section needed a real `TRANSACTION_VIEW` check for the first time, so `EmployeeJwtAuthenticationFilter` was upgraded to set a proper `EmployeePrincipal` (mirroring account-service's own type, minus the `branchId`/`branchIfsc` fields this service has no use for) carrying real `GrantedAuthority`s, and `@EnableMethodSecurity` was added to `SecurityConfig`. The existing `POST /api/v1/transactions` endpoint's own behavior — accepts any valid token, customer or employee, checks nothing beyond that — is completely unchanged; only the *shape* of what "employee-authenticated" means internally changed.

## beneficiary-service: its first employee token, ever

Before this phase, beneficiary-service had no employee-JWT support of any kind — Customer 360's beneficiaries section is the first employee-originated request this service has ever needed to accept. The full quartet (`EmployeeJwtProperties`/`EmployeeJwtValidator`/`EmployeePrincipal`/`EmployeeJwtAuthenticationFilter`/`EmployeeCurrentUser`) was added fresh, mirroring account-service's Phase 9B shape exactly, gating the new `GET /api/v1/beneficiaries/employee/customer/{customerId}` on `CUSTOMER_VIEW` — deliberately reused rather than adding a new `BENEFICIARY_VIEW` permission that would exist for exactly one read-only section.

## Branch scope: KYC is explicitly not branch-scoped

Per the task's own explicit instruction ("do not invent a branch_id unless the domain actually requires it — if globally reviewable, document that decision"), and consistent with the Phase 9B/9C inspection finding that `Account`/`Customer` have no branch field: a `KycApplication` has no natural branch anchor the way `Account.ifsc` gave cash deposit one. Inventing a `branch_id` column on `KycApplication` would have repeated exactly the mistake ADR-007 rejected for `Account` itself. KYC review is globally reviewable by any employee holding the relevant `KYC_*` permission — `EmployeePrincipal.branchId`/`branchIfsc` are still carried and logged for audit-trail completeness, never read for an authorization decision. Documented explicitly in ADR-008 rather than left as an implicit gap.

## RBAC change: minimal, inspected first

Only `KYC_REJECT` was added to the `Permission` enum — symmetric with the pre-existing `KYC_APPROVE` (both are terminal, whole-application decisions; document-level verify/reject stays under the existing `KYC_REVIEW`). `KYC_OFFICER` gained `KYC_REJECT` and `TRANSACTION_VIEW` (a real AML/KYC risk-assessment need, and required for that role to see Customer 360's transactions section). `BRANCH_MANAGER` deliberately did **not** gain `KYC_REJECT`, symmetric with its pre-existing, unchanged lack of `KYC_APPROVE`. No other role's KYC access changed. Verified by running employee-service's full test suite (83 tests, including the parameterized RBAC tests pinned against the real mapping) immediately after the change, before writing any new code on top of it.

## Frontend: a new file-upload pattern, a new nested-nav pattern

Neither frontend had any prior file-upload UI or multipart-request code (`grep`-confirmed zero matches for `multipart`/`FormData`/`type="file"` across the customer frontend). `components/forms/FileUpload.tsx` was built fresh, to the same `label`/`hint`/`error` contract every other form control in the codebase already uses, rather than introducing a visually distinct upload widget.

employee-portal's `AppLayout.tsx` had never rendered a nested nav item — every existing `ModuleCatalogEntry` was a flat link or an inert "Coming soon" badge. "KYC & Compliance" is the first module with more than one real page, so `ModuleCatalogEntry` gained an optional `children` array, and `AppLayout.tsx`'s render loop was extended to render a permission-filtered sub-list — each child independently checked against `hasPermission`, so e.g. a `KYC_VIEW`-only employee sees "KYC Queue" and "Completed Reviews" but not "Document Verification" (`KYC_REVIEW`-gated).

One deliberate, documented gap: "Review Application" (one of the four nav items the task named) is not its own nav destination — it has no meaning without a specific application id. It's reached by selecting a row in either "KYC Queue" or "Document Verification," not from the sidebar. Recorded as a considered decision, not an oversight, in both `navigationCatalog.ts`'s own comment and `docs/architecture/customer-360-and-kyc.md`.

## A real E2E scripting bug found and fixed mid-run (not an application bug)

The first real-browser Puppeteer run of the full customer-submits → employee-approves journey failed at the document-upload step: the script grabbed all four `<input type="file">` element handles once, up front, then uploaded to each in a loop. After the *first* upload succeeded, the customer page's `reload()` call caused React to re-render that document's control from an upload button into an "uploaded" display — replacing the DOM node entirely — which silently detached the *other three* previously-grabbed handles from the live document. Only 1 of 4 uploads actually took effect; the submit button correctly stayed disabled given the real, correctly-computed `missingDocumentTypes`. Not an application bug — the frontend behaved exactly as designed and the screenshot proved it (`PENDING` badge showing for the one real upload, "Choose file" still showing for the other three, submit disabled). Fixed by re-querying `input[type="file"]` fresh before each individual upload rather than grabbing all handles up front. Also found and fixed in the same pass: a native `<input type="date">` fed via `.type()` character-by-character produces garbage (`"12/09/30620"` from `"1993-06-20"`) — fixed by setting `.value` directly via the native property setter and dispatching `input`/`change` events.

## Live verification — HTTP and browser layers

**Live HTTP (`curl`), full lifecycle**: registered a real customer, created a KYC application, uploaded all four required documents, submitted (`DRAFT → SUBMITTED`), logged in as the seeded `kim.kyc` (`KYC_OFFICER`) employee, confirmed the application appeared in the queue with `4/4` documents, started review (`SUBMITTED → UNDER_REVIEW`), verified all four documents, approved (`UNDER_REVIEW → APPROVED`) — and confirmed the customer's own `GET /api/v1/kyc/applications/me` immediately reflected `APPROVED`, with no cache/propagation delay (kyc-service is the single source of truth for both portals).

**Live HTTP, negative/security cases**: a second, independently registered customer denied `403` reading the first customer's application by its real UUID (`KycAccessDeniedException`); the seeded `jane.teller` (`TELLER`, lacking `KYC_APPROVE`) denied `403` attempting to approve; a customer token denied `403` on `GET /api/v1/kyc/employee/queue`; no token `401`; submitting an application with zero documents `422` naming the exact missing types; an employee attempting to approve a `DRAFT` application directly `422` ("Cannot transition KYC application from DRAFT to APPROVED"); a second `POST /api/v1/kyc/applications` for a customer with an existing non-terminal application `409`.

**Live HTTP, Customer 360**: `GET /api/v1/employee/customers/{customerId}/360` as the `KYC_OFFICER` (holding all four view permissions) returned every section `available: true` with real data — the customer's profile, the (empty, since this test customer had none) accounts/transactions/beneficiaries, and the just-approved KYC application with all four documents `VERIFIED` — plus `unavailableCapabilities: ["LOANS","CARDS","FOREX","SERVICE_REQUESTS"]`, never fabricated.

**Real browser (Puppeteer against system Chrome)**: after fixing the two scripting bugs above, a full run drove the actual compiled customer portal through register → login → start a KYC application (real form) → upload all four documents (real native file inputs) → submit (screenshot confirms `SUBMITTED` badge + toast), then the actual compiled employee portal through login → KYC Queue (showing the real application, `4/4` documents) → Review Application → Start Review → Verify all four documents → Approve Application (screenshot confirms `APPROVED` badge, all four documents `VERIFIED`, and a correctly-ordered real review history: `DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED`, each with a real timestamp). The final "customer reloads and sees APPROVED" step was independently confirmed via the `curl` flow above rather than the browser script's own last step, which hit an unrelated `networkidle0` timeout (a persistent widget connection on the customer portal keeps the network non-idle) — not an application defect, and not claimed as browser-verified beyond what the screenshots actually show.

## Docker environment note (unchanged from every prior phase)

No `docker compose` binary (v1 or v2) is available in this environment — confirmed again this phase (`docker: unknown command: docker compose`, no cli-plugin installed anywhere on the host). `docker-compose.yml` was still updated (new `kyc-service` block, new env vars on `employee-service`/`frontend`/`employee-portal`, new named volume) as the source-of-truth reference for an environment where compose *is* available, but the actual verification in this environment was done by building each of the six changed/new images individually (`docker build`) and wiring them by hand with `docker network`/`docker run`, replicating `docker-compose.yml`'s env vars/ports/network exactly — the same substitute method every prior phase has used.

## Build and test results

**Backend — 348 tests passing across all six services, zero regressions:**
- `account-service`: 90 tests (unaffected — no changes this phase)
- `customer-service`: 49 tests (+6 for the new `employee-lookup/{id}/profile` endpoint)
- `transaction-service`: 20 tests (+5 for the new employee endpoint + `EmployeePrincipal` upgrade)
- `beneficiary-service`: 30 tests (+5 for the new employee endpoint + fresh dual-JWT quartet)
- `employee-service`: 93 tests (+10 for Customer 360's controller/service, on top of the RBAC change)
- `kyc-service`: 66 tests — new service (unit + `@WebMvcTest` controller + `KycApplicationReviewConcurrencyIT` against real Postgres)

**Frontend — zero regressions, no new automated tests added this phase** (a real, acknowledged gap given the time this phase's scope required — `Kyc.tsx`, `Customer360.tsx`, and the KYC review screens were verified via live `curl` and real-browser Puppeteer instead, not Vitest/RTL):
- `frontend` (customer portal): `tsc -b && vite build` clean; 37 existing Vitest tests still pass
- `employee-portal`: `tsc -b && vite build` clean; 24 existing Vitest tests still pass

## What was deliberately not built this phase

AI-assisted document verification (documented as a future, advisory-only extension point in ADR-008 — never autonomous approval), a real Notification Service and its four KYC trigger points, S3-backed document storage, a maker-checker model beyond optimistic-lock conflict protection, Kafka/Outbox for real event publishing, a re-KYC flow after a terminal decision, branch-scoped KYC (a deliberate rejection, not a deferral), loans/cards/forex/service-requests in Customer 360 (reported as `unavailableCapabilities`, never fabricated) — all explicitly out of scope per the task's own repeated instruction, and all recorded in ADR-008's "Deferred to a later phase" section.
