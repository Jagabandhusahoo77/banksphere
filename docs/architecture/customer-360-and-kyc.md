# Customer 360 and KYC

_Status: Phase 9C — adds a sixth microservice (kyc-service), a full customer/employee KYC application-and-review workflow, and an employee-facing Customer 360 aggregation. Builds on Phase 9A's employee identity/RBAC foundation (`docs/architecture/employee-platform.md`) and Phase 9B's employee-operations pattern (`docs/architecture/employee-operations.md`), both unmodified baseline here. See [ADR-008](decisions/ADR-008-kyc-domain-and-document-storage.md) for the full design reasoning behind every decision summarized here._

## The two flows

```
CUSTOMER PORTAL (customer JWT)                    EMPLOYEE PORTAL (employee JWT)
        │                                                    │
        ▼                                                    ▼
 kyc-service                                            kyc-service
 /api/v1/kyc/applications/**                            /api/v1/kyc/employee/**
   create → upload documents → submit                     queue → application detail
   (DRAFT → SUBMITTED)                                     → start-review (→ UNDER_REVIEW)
                                                             → verify/reject each document
                                                             → request-information
                                                               (→ ADDITIONAL_INFORMATION_REQUIRED)
                                                             → approve/reject
                                                               (→ APPROVED/REJECTED)
        │                                                    │
        └──────────────── same kyc-service, same database ───┘

 EMPLOYEE PORTAL — Customer 360
        │  employee JWT
        ▼
 employee-service — GET /api/v1/employee/customers/{customerId}/360
   │  forwards the employee's OWN bearer token to each ↓
   ├─→ customer-service    (customer profile)
   ├─→ account-service     (accounts)
   ├─→ transaction-service (recent transactions, per account)
   ├─→ beneficiary-service (active beneficiaries)
   └─→ kyc-service         (current KYC application, if any)
```

Both KYC endpoint groups live in the same service and the same database — kyc-service is a genuine bounded domain, not something proxied through employee-service (unlike Customer 360, which genuinely is an aggregation employee-service performs on the caller's behalf). See ADR-008, Decisions 1–3.

## kyc-service

A sixth independent Spring Boot service, its own database (`banksphere_kyc`), following the exact per-service pattern every prior service uses — no shared module, no shared database, no cross-database foreign keys. `customerId`/`reviewedBy`/`currentReviewerId`/`changedByEmployeeId` reference other services' primary keys by UUID value only.

### Domain model

- `KycApplication` — `id`, `customerId`, `status`, `panNumber`/`occupation`/`annualIncomeRange` (illustrative demo fields), `currentReviewerId`, `submittedAt`/`reviewedAt`/`reviewedBy`, `reviewReason` (always customer-visible), `version` (`@Version`, optimistic locking — see "Concurrency" below).
- `KycDocument` — `id`, `kycApplicationId`, `documentType` (`PAN`/`IDENTITY_PROOF`/`ADDRESS_PROOF`/`BANK_STATEMENT` — not a claim of legal completeness), `documentStatus` (`PENDING`/`VERIFIED`/`REJECTED`), `storageReference` (opaque, never exposed — see "Document storage" below), `originalFileName`, `contentType`, `fileSize`, `rejectionReason`.
- `KycStatusHistory` — a real, queryable transition log (`fromStatus`, `toStatus`, `changedByEmployeeId` — null for customer-initiated, never fabricated — `reason`, `changedAt`), distinct from the free-form `KycAuditLog` log lines (see "Audit" below).

### State machine

```
DRAFT ─submit──────────────────────────────► SUBMITTED
                                                  │ start-review
                                                  ▼
                                            UNDER_REVIEW ──approve─────► APPROVED (terminal)
                                                  │        ──reject──────► REJECTED (terminal)
                                                  │
                                                  └─request-information─► ADDITIONAL_INFORMATION_REQUIRED
                                                                              │ resubmit
                                                                              ▼
                                                                        RESUBMITTED
                                                                              │ start-review
                                                                              ▼
                                                                        UNDER_REVIEW (loop)
```

`KycStateMachine` (a single `Map<KycStatus, Set<KycStatus>>`, `service` package) is the one authoritative table every mutation funnels through — `requireValidTransition` throws `InvalidStateTransitionException` (`422`) for anything outside it. `APPROVED`/`REJECTED` are both terminal; `APPROVED → DRAFT`, `APPROVED → REJECTED`, `REJECTED → APPROVED` are all rejected by construction. Resubmission reuses the identical `start-review` action to move `RESUBMITTED → UNDER_REVIEW` — no special-cased second endpoint. See ADR-008, Decision 9.

### Document storage

`DocumentStorage` (an interface, `storage` package) — `store(kycApplicationId, originalFileName, content) -> storageReference` / `load(storageReference) -> content`. `LocalDocumentStorage`, the only implementation this phase builds, writes to the filesystem at `KYC_DOCUMENT_STORAGE_PATH` (default `/data/kyc-documents`, a Docker named volume). The stored filename is always a freshly generated UUID, never the caller's `originalFileName`. `storageReference` never crosses into any customer- or employee-facing DTO — the one place stored bytes are returned is `GET /api/v1/kyc/employee/documents/{id}/content` (`KYC_REVIEW`-gated), which streams the bytes with their real content type. A future `S3DocumentStorage` is a drop-in replacement — no KYC business logic would change. AWS/S3 is not introduced this phase. See ADR-008, Decision 10.

### Security

Two independent JWT validators, added alongside each other (never one replacing the other) — the same dual-secret pattern ADR-006/ADR-007 established:

| Path prefix | Token | Identity source | Notes |
|---|---|---|---|
| `/api/v1/kyc/applications/**` | Customer JWT (`JWT_SECRET`) | `CurrentUser.id` (JWT subject) | An employee token reaching here gets `403` via `WrongPrincipalTypeException` — a small, deliberate improvement on the "unexplained 400" rough edge ADR-007 recorded for the equivalent case elsewhere. |
| `/api/v1/kyc/employee/**` | Employee JWT (`EMPLOYEE_JWT_SECRET`) | `EmployeeCurrentUser.identity` | Gated by `KYC_VIEW` at minimum (`SecurityConfig`), plus a stronger `@PreAuthorize` per action (see RBAC table below). A customer token never carries any `KYC_*` authority, so it's rejected with `403` before reaching the cast. |

No request body ever supplies `customerId` or `employeeId` for an access or audit decision — both always come from the caller's own verified token.

### RBAC

| Permission | Gates |
|---|---|
| `KYC_VIEW` | Queue, application detail, Customer 360's KYC section |
| `KYC_REVIEW` | start-review, verify/reject document, request-information |
| `KYC_APPROVE` | Final approve |
| `KYC_REJECT` | Final reject |

`KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE` already existed in the Phase 9A catalog (unused until this phase); only `KYC_REJECT` was added — symmetric with `KYC_APPROVE` (both final decisions, neither folded into `KYC_REVIEW`, which gates in-progress actions only). `KYC_OFFICER` gained `KYC_REJECT` and `TRANSACTION_VIEW` (a real AML/KYC need, and required for Customer 360's transactions section). `BRANCH_MANAGER` deliberately does not gain `KYC_REJECT`, symmetric with its pre-existing lack of `KYC_APPROVE`. No other role's KYC access changed. See ADR-008, Decision 8, and `docs/architecture/employee-platform.md`'s full role table.

### Branch scope

KYC is globally reviewable — **not** branch-scoped. Unlike `Account.ifsc` for cash deposit (ADR-007), a `KycApplication` has no natural branch anchor, and inventing one would violate the same "don't invent a `branch_id` unless the domain requires it" principle ADR-007 itself established. `EmployeePrincipal.branchId`/`branchIfsc` are still carried and logged for audit-trail completeness, never read for an authorization decision. See ADR-008, Decision 11.

### Audit

`KycAuditLog` (dedicated `com.banksphere.kyc.AUDIT` logger, mirroring `EmployeeAuditLog`) writes one structured line per event: `KYC_APPLICATION_SUBMITTED`, `KYC_DOCUMENT_UPLOADED`, `KYC_REVIEW_STARTED`, `KYC_DOCUMENT_VERIFIED`, `KYC_DOCUMENT_REJECTED`, `KYC_ADDITIONAL_INFORMATION_REQUESTED`, `KYC_APPROVED`, `KYC_REJECTED` — each carrying `employeeId`/`employeeNumber`/`branchId` (null for customer-initiated), `customerId`, `applicationId`, `documentId`, `result`, `timestamp`, `correlationId`. No Kafka, no Outbox, no real Audit Service yet — the event shape is chosen so a future pipeline could adopt it unchanged. See ADR-008, Decision 12.

### Concurrency

`KycApplication.version` (`@Version Long`) — identical mechanism to `Account.version` since Phase 7A. Two employees opening the same application, one approving and one attempting to reject: the loser's transaction throws `ObjectOptimisticLockingFailureException`, mapped to `409`. Every mutation call site uses `saveAndFlush` (not `save`) specifically so the conflict is detected — and any audit line reflects reality — before the transaction commits, not after; this was caught as a real bug during this phase's own concurrency IT (an early version logged `KYC_REJECTED … result=SUCCESS` for the losing transaction, a moment before the database rejected it). Proven by `KycApplicationReviewConcurrencyIT`, a real-Postgres integration test, using the same `PostgresAssumptions.assumeReachable()` skip-if-unreachable pattern as `AccountTransferConcurrencyIT`.

### API endpoints

**Customer-facing** (`/api/v1/kyc/applications`):

| Endpoint | Purpose |
|---|---|
| `POST /` | Create (customerId from JWT; `409` if a non-terminal application already exists) |
| `GET /me` | The caller's own most recent application |
| `GET /{id}` | A specific application (ownership-checked) |
| `POST /{id}/documents` | Upload a document (multipart; `documentType` query param) |
| `POST /{id}/submit` | `DRAFT → SUBMITTED` |
| `POST /{id}/resubmit` | `ADDITIONAL_INFORMATION_REQUIRED → RESUBMITTED` |

**Employee-facing** (`/api/v1/kyc/employee`):

| Endpoint | Permission | Purpose |
|---|---|---|
| `GET /queue?status=` | `KYC_VIEW` | The review queue, optional single-status filter |
| `GET /applications/{id}` | `KYC_VIEW` | Full detail, including status history and `missingDocumentTypes` |
| `GET /customer/{customerId}` | `KYC_VIEW` | The Customer 360 lookup — `204` (not `404`) if the customer never started KYC |
| `POST /applications/{id}/start-review` | `KYC_REVIEW` | `SUBMITTED`/`RESUBMITTED → UNDER_REVIEW` |
| `POST /documents/{id}/verify` | `KYC_REVIEW` | Mark one document `VERIFIED` |
| `POST /documents/{id}/reject` | `KYC_REVIEW` | Mark one document `REJECTED`, with reason |
| `GET /documents/{id}/content` | `KYC_REVIEW` | Streams the document's real bytes/content-type |
| `POST /applications/{id}/request-information` | `KYC_REVIEW` | `UNDER_REVIEW → ADDITIONAL_INFORMATION_REQUIRED`, with reason |
| `POST /applications/{id}/approve` | `KYC_APPROVE` | `UNDER_REVIEW → APPROVED` |
| `POST /applications/{id}/reject` | `KYC_REJECT` | `UNDER_REVIEW → REJECTED`, with reason |

## Customer 360

An authorized, live aggregation — not a data copy. `Customer360ServiceImpl` (employee-service) calls five services on the caller's behalf, forwarding their own bearer token to each; nothing is persisted in employee-service beyond the request. See ADR-008, Decision 3.

### New endpoints this phase feeds it

| Endpoint | Service | Note |
|---|---|---|
| `GET /api/v1/customers/employee-lookup/{id}/profile` | customer-service | Additive — the Phase 9B slim `employee-lookup/{id}` is untouched |
| `GET /api/v1/accounts/employee-lookup/customer/{customerId}` | account-service | Already existed since Phase 9B, reused as-is |
| `GET /api/v1/transactions/employee/account/{accountId}` | transaction-service | New — first `@PreAuthorize`-gated employee endpoint on this service |
| `GET /api/v1/beneficiaries/employee/customer/{customerId}` | beneficiary-service | New — this service's first-ever employee-token acceptance |
| `GET /api/v1/kyc/employee/customer/{customerId}` | kyc-service | See above |

### Section-level graceful degradation

`GET /api/v1/employee/customers/{customerId}/360` — gated by `@PreAuthorize("hasAnyAuthority('CUSTOMER_VIEW','ACCOUNT_VIEW','TRANSACTION_VIEW','KYC_VIEW')")` as a floor. The response is five independent `Customer360Section<T>` values (`{available, unavailableReason, data}`), each decided against the caller's *actual* full permission set: a section the caller isn't authorized for comes back `available: false` with a stated reason — never silently omitted (which would look like "no data") and never fabricated. This is this codebase's first section-level (rather than whole-endpoint) permission model. See ADR-008, Decision 4.

`unavailableCapabilities` (`LOANS`, `CARDS`, `FOREX`, `SERVICE_REQUESTS`) is a separate, static list naming domains that don't exist *at all* yet, for *any* caller — never fabricated data for a feature that has no backend, per CLAUDE.md.

### Section → permission

| Section | Permission |
|---|---|
| `customer` | `CUSTOMER_VIEW` |
| `accounts` | `ACCOUNT_VIEW` |
| `transactions` | `ACCOUNT_VIEW` **and** `TRANSACTION_VIEW` (accounts must be known first to look up their transactions) |
| `beneficiaries` | `CUSTOMER_VIEW` (reused — no new `BENEFICIARY_VIEW` permission; see ADR-008, Decision 7) |
| `kyc` | `KYC_VIEW` |

## Frontend

### Customer portal (`frontend/`)

New "KYC" nav item (`/kyc`). One page, `pages/kyc/Kyc.tsx`, state-driven on the caller's current application: no application → start form (PAN/occupation/income); `DRAFT`/`ADDITIONAL_INFORMATION_REQUIRED` → document upload (`components/forms/FileUpload.tsx`, this codebase's first file-upload control, built to the same label/hint/error contract as `Input`/`Select`) + submit/resubmit; `SUBMITTED`/`UNDER_REVIEW`/`RESUBMITTED` → status banner; `APPROVED`/`REJECTED` → terminal state with the real reviewed date/reason. `useKycApplication` is a dedicated hook (not a thin `useAsync` wrapper) because a `404` on `GET .../me` means "never started KYC," a normal state, not a fetch error.

### Employee portal (`employee-portal/`)

"Customer 360" and "KYC & Compliance" added to `navigationCatalog.ts` — the latter is this portal's first nested nav entry (`AppLayout.tsx` extended to render sub-items, each independently permission-filtered). Customer 360 reuses the existing account-number search step (`Customer360Search.tsx`, mirroring `CashDeposit.tsx`'s "Find Customer" step — no separate name/email search endpoint exists) before landing on `Customer360.tsx`. KYC & Compliance: `KycQueue.tsx` (`SUBMITTED`/`RESUBMITTED`/`ADDITIONAL_INFORMATION_REQUIRED`), `KycUnderReview.tsx` (labeled "Document Verification" in nav — applications currently `UNDER_REVIEW`, where document verify/reject actually happens), `KycCompleted.tsx` (`APPROVED`/`REJECTED`), and `KycReviewApplication.tsx` (the actual review screen — document verify/reject, request-information, approve/reject, each gated inline on `hasPermission("KYC_REVIEW"/"KYC_APPROVE"/"KYC_REJECT")`, mirroring the backend's own per-action `@PreAuthorize`). "Review Application" is not its own nav destination — it has no meaning without a specific application id, so it's reached by selecting a row in either queue view.

## Future extension points (not built this phase)

- **AI-assisted document verification** — advisory only, never autonomous; the natural integration point is an additive field on the document/application response DTOs, populated by a future service kyc-service would call, using the same "authorized aggregation, never trust the automated signal for the decision" shape already established for Customer 360. `approve`/`reject` remain `@PreAuthorize`-gated human actions regardless.
- **Notifications** — `KYC_SUBMITTED`/`KYC_ADDITIONAL_INFORMATION_REQUIRED`/`KYC_APPROVED`/`KYC_REJECTED` map directly onto `KycApplicationServiceImpl.transition`'s existing call sites; a future Notification Service would hook in there.
- **S3-backed document storage**, **Kafka/Outbox for real event publishing**, **a maker-checker model beyond optimistic-lock conflict protection**, **a re-KYC flow after a terminal decision** — see ADR-008's "Deferred to a later phase."
