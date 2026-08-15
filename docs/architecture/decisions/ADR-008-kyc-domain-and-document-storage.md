# ADR-008: KYC Domain, Customer 360, and Document Storage

**Status:** Accepted for Phase 9C

## Context

Phase 9B built the first real employee-originated banking operation (branch cash deposit) but left the employee portal with no way to see a customer's full picture, and left BankSphere with no KYC (Know Your Customer) capability at all — a real gap for a banking product. This phase builds both: a KYC application/document/review workflow spanning both portals, and a Customer 360 aggregated view for employees. The task's own framing set the central constraint: *"The employee portal is a CHANNEL, not the owner of every banking domain. Do NOT turn employee-service into a monolith."* Every decision below follows from taking that literally.

## Decision 1 — KYC is a new, dedicated bounded domain: `kyc-service`

KYC applications, documents, document verification state, and review decisions do not belong to any existing service's domain. `employee-service` owns employee identity/roles/branches, not customer-facing workflow state; `customer-service` owns the customer profile, not a stateful, multi-actor review process. Rather than bolting KYC onto either, this phase adds a sixth independent service, `kyc-service`, with its own database (`banksphere_kyc`) — following the exact database-per-service pattern every prior service already uses. No cross-database foreign keys: `customerId`, `reviewedBy`, `currentReviewerId`, and `changedByEmployeeId` are all UUID values referencing another service's primary key by convention only, the same pattern `Beneficiary.customerId` and `CashDepositOperation.customerId` already established.

## Decision 2 — kyc-service is called directly by both portals, not proxied through employee-service

Two structurally different options existed for the employee-facing side: (a) route employee KYC actions through employee-service's existing "Employee Operations API" pattern (as Phase 9B did for cash deposit, where employee-service owned no domain data of its own and account-service was the sole source of truth), or (b) give kyc-service its own complete employee-facing API surface, called directly. Cash deposit's shape doesn't fit KYC: KYC is a genuine bounded domain with real data and real state of its own — closer to how `beneficiary-service` is called directly by the customer frontend than to how account-service is orchestrated by employee-service on a customer's behalf. kyc-service therefore exposes two complete API surfaces directly — `/api/v1/kyc/applications/**` for customer-portal, `/api/v1/kyc/employee/**` for employee-portal — each with its own JWT validation (Decision 4/5), and neither proxied through another service.

## Decision 3 — Customer 360 is an authorized aggregation, not a data copy

Customer 360 needs data that spans five services (customer, account, transaction, beneficiary, kyc). The rejected approach: replicate customer/account/transaction data into employee-service's own database, keeping it in sync somehow. That would create a second, inevitably-stale source of truth for banking data — exactly what "the employee portal is a channel, not the owner" forbids. The chosen approach: `employee-service` extends its existing Phase 9B aggregation pattern (`OperationsController`/`OperationsServiceImpl`, which already calls account-service and customer-service on the caller's behalf) with three new REST clients (`TransactionLookupClient`, `BeneficiaryLookupClient`, `KycLookupClient`) and one new endpoint, `GET /api/v1/employee/customers/{customerId}/360`. Every field in the response is fetched live, on every request, from the five owning services — nothing is persisted in employee-service beyond the request's lifetime. `Customer360ServiceImpl`'s own javadoc states this explicitly.

Four new employee-facing endpoints, one per downstream service, feed this aggregation:
- customer-service: `GET /api/v1/customers/employee-lookup/{id}/profile` (new, additive — the existing Phase 9B `employee-lookup/{id}` slim endpoint is untouched, since cash deposit's confirmation UI doesn't need the fuller profile this adds).
- account-service: `GET /api/v1/accounts/employee-lookup/customer/{customerId}` (already existed since Phase 9B — reused as-is, no account-service change needed).
- transaction-service: `GET /api/v1/transactions/employee/account/{accountId}` (new — see Decision 6).
- beneficiary-service: `GET /api/v1/beneficiaries/employee/customer/{customerId}` (new — beneficiary-service's first-ever acceptance of an employee token; see Decision 6).
- kyc-service: `GET /api/v1/kyc/employee/customer/{customerId}` (new — returns `204 No Content`, not `404`, when the customer has never started KYC, since that is a normal, common state for this specific lookup, not an error).

## Decision 4 — section-level graceful degradation, a new pattern for this codebase

Every prior permission-gated endpoint in this codebase is all-or-nothing: the caller either holds the required permission and gets the whole response, or gets a `403`. Customer 360 can't work that way — a `LOAN_OFFICER` and a `KYC_OFFICER` both have a legitimate reason to open the same customer's 360 view, but each is authorized for a different subset of it (per the existing Phase 9A/9C `RolePermissions` mapping — see Decision 8). Refusing the whole view to the officer with the narrower permission set would make Customer 360 useless for most roles.

The response is a struct of five `Customer360Section<T>` values (`customer`, `accounts`, `transactions`, `beneficiaries`, `kyc`), each independently `{available, unavailableReason, data}`. The controller's `@PreAuthorize` is only a floor — a caller needs at least one of `CUSTOMER_VIEW`/`ACCOUNT_VIEW`/`TRANSACTION_VIEW`/`KYC_VIEW` to reach the endpoint at all — and `Customer360ServiceImpl` then decides, section by section, against the caller's *actual* full permission set (extracted from their JWT authorities at the controller and passed down), whether to call the corresponding downstream service or return `available: false` with a stated reason. A section a caller isn't authorized for is never silently omitted (which would look like "this customer has no accounts" instead of "you can't see accounts") and never fabricated — it's explicitly marked unavailable, with why.

This is distinct from `unavailableCapabilities`, a static list (`LOANS`, `CARDS`, `FOREX`, `SERVICE_REQUESTS`) naming domains that don't exist in this codebase *for any caller*, regardless of permission — the literal application of this phase's "display future capabilities as unavailable, never fabricate" instruction.

## Decision 5 — customer identity and employee identity, both from the verified JWT only

kyc-service validates two structurally distinct token types, exactly as account-service/transaction-service/beneficiary-service now do (see ADR-006/007): `JwtValidator` (customer-service's `JWT_SECRET`) and `EmployeeJwtValidator` (employee-service's `EMPLOYEE_JWT_SECRET`) — two separate keys, two separate filters, added alongside each other in `SecurityConfig`, never one replacing the other.

`/api/v1/kyc/applications/**` requires only "authenticated"; `CurrentUser.id(authentication)` derives the customer id from the verified JWT subject and additionally guards against the wrong principal type: if an employee token reaches one of these endpoints, `CurrentUser.id` throws `WrongPrincipalTypeException` (mapped to `403`) rather than the "fails loudly as an unexplained `400`" behavior ADR-007 recorded as a known rough edge for the equivalent case elsewhere. This is a small, deliberate improvement on that precedent, scoped only to kyc-service's own new code — no existing service's `CurrentUser` was touched to backport it.

`/api/v1/kyc/employee/**` requires `KYC_VIEW` at minimum (enforced by `SecurityConfig`) plus a stronger, action-specific permission per endpoint via `@PreAuthorize` (Decision 8) — a customer token never carries any `KYC_*` authority, so it is rejected with `403` before ever reaching `EmployeeCurrentUser.identity`. No request body ever supplies `customerId` or `employeeId` for an access or audit decision — both always come from the caller's own verified token.

## Decision 6 — three existing services accept an employee token for the first time (or for a new reason)

- `transaction-service` already accepted an employee-signed token (Phase 9B, for `POST /api/v1/transactions`) but never checked *which* permissions it carried — the principal was a bare, defensively-shaped string. This phase upgrades it to a proper `EmployeePrincipal` (mirroring account-service's own type, minus `branchId`/`branchIfsc` — transaction-service has no branch-scoped logic to carry them for) and adds `@EnableMethodSecurity`, since Customer 360's transactions section needs a real `@PreAuthorize("hasAuthority('TRANSACTION_VIEW')")` check for the first time. The existing `POST /api/v1/transactions` endpoint's behavior (accepts any valid token, customer or employee, no further identity check — ADR-001) is completely unchanged.
- `beneficiary-service` had no employee-token support at all before this phase — Customer 360's beneficiaries section is the first employee-originated request this service has ever needed to accept. The full quartet (`EmployeeJwtProperties`/`EmployeeJwtValidator`/`EmployeePrincipal`/`EmployeeJwtAuthenticationFilter`/`EmployeeCurrentUser`) was added fresh, mirroring account-service's Phase 9B shape exactly.
- Both additions required an `AccessDeniedException` handler in each service's `GlobalExceptionHandler` — its absence would have let the new `@PreAuthorize` checks fall through to the generic `500` handler instead of `403`, the exact class of bug ADR-006 (Decision 4) already found and fixed once in employee-service; caught and fixed proactively here before it could recur, not discovered by accident.

## Decision 7 — Customer 360's beneficiaries section reuses `CUSTOMER_VIEW`, not a new permission

A `BENEFICIARY_VIEW` permission was considered and rejected: it would exist for exactly one read-only aggregation section, adding a permission-catalog entry for a distinction (`CUSTOMER_VIEW` vs seeing a customer's own beneficiaries) no role in the existing Phase 9A/9C mapping actually needs to distinguish. `CUSTOMER_VIEW` already gates "can this employee see this customer's identifying information" — beneficiary names/account numbers are exactly that category of data, so the beneficiaries section is gated by it too.

## Decision 8 — RBAC: `KYC_REJECT` added; existing mapping inspected first, changed minimally

`KYC_VIEW`, `KYC_REVIEW`, and `KYC_APPROVE` already existed from ADR-006's original catalog design (unused until this phase). Only `KYC_REJECT` was missing — added as its own permission, symmetric with `KYC_APPROVE`: a final `REJECT` decision on a whole application is as significant an authority as a final `APPROVE`, so it is not folded into `KYC_REVIEW` (which gates in-progress actions — document verify/reject, request-information — not the terminal application-level decision).

The mapping was inspected before any change, not assumed. Confirmed: `KYC_OFFICER` already held `KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE`; `BRANCH_MANAGER` already held `KYC_VIEW`/`KYC_REVIEW` but not `KYC_APPROVE` (an ADR-006 design choice, unchanged); `TELLER`/`LOAN_OFFICER`/`CARD_OFFICER`/`ADMIN`/`OPERATIONS` held none. Changes made: `KYC_OFFICER` gains `KYC_REJECT` (symmetric with its existing `KYC_APPROVE`) and `TRANSACTION_VIEW` (a real AML/KYC risk-assessment need, and required for Customer 360's transactions section to be visible to a KYC officer reviewing an application). `BRANCH_MANAGER` deliberately does **not** gain `KYC_REJECT`, symmetric with its pre-existing lack of `KYC_APPROVE`. No other role's KYC-related access changed. Document verify/reject/start-review/request-information are gated by `KYC_REVIEW`; the two terminal application decisions are gated by `KYC_APPROVE`/`KYC_REJECT` respectively.

## Decision 9 — the KYC state machine: one authoritative table, no ad hoc checks

`KycStateMachine` (kyc-service, `service` package) is a single `Map<KycStatus, Set<KycStatus>>` static table:

```
DRAFT                            -> SUBMITTED
SUBMITTED                        -> UNDER_REVIEW
UNDER_REVIEW                     -> APPROVED, REJECTED, ADDITIONAL_INFORMATION_REQUIRED
ADDITIONAL_INFORMATION_REQUIRED  -> RESUBMITTED
RESUBMITTED                      -> UNDER_REVIEW
APPROVED                         -> (terminal)
REJECTED                         -> (terminal)
```

Every mutation that changes `KycApplication.status` — customer-initiated (`submit`, `resubmit`) and employee-initiated (`start-review`, `request-information`, `approve`, `reject`) alike — funnels through `KycStateMachine.requireValidTransition`, which throws `InvalidStateTransitionException` (mapped to `422`) for anything outside this table. `APPROVED -> DRAFT`, `APPROVED -> REJECTED`, `REJECTED -> APPROVED` are all rejected by construction, not by a scattered set of per-endpoint checks. Resubmission deliberately reuses the identical `start-review` employee action to move `RESUBMITTED -> UNDER_REVIEW` — no special-cased second "resume review" endpoint. A re-KYC flow starting a fresh application after a terminal decision is explicitly out of scope for this phase (see "Deferred").

Every transition is additionally recorded as a `KycStatusHistory` row (`fromStatus`, `toStatus`, `changedByEmployeeId` — null for a customer-initiated transition, never fabricated — `reason`, `changedAt`), a real queryable table distinct from the free-form `KycAuditLog` log lines (Decision 12): this one is for the review screen's "review history" panel to render directly; the audit log is for a future log shipper/Audit Service.

## Decision 10 — document storage: an interface, filesystem today, S3-ready by construction

`DocumentStorage` (kyc-service, `storage` package) is a two-method interface — `store(kycApplicationId, originalFileName, content) -> storageReference` and `load(storageReference) -> content` — that the KYC domain and service layer depend on exclusively; no class outside the `storage` package imports an AWS SDK type or touches `java.nio.file` directly. `LocalDocumentStorage`, the only implementation this phase builds, writes to the filesystem at `KYC_DOCUMENT_STORAGE_PATH` (default `/data/kyc-documents`, a Docker named volume — see `docker-compose.yml`'s `banksphere-kyc-documents` volume). The stored filename is always a freshly generated UUID, never the caller-supplied `originalFileName` — defending against both path traversal via a crafted filename and same-name collisions between unrelated uploads; the original filename is preserved only as metadata on `KycDocument`, never used to address the file on disk. `storageReference` is stored verbatim on `KycDocument.storageReference` and is never serialized into any customer- or employee-facing DTO — every `KycDocumentResponse` deliberately omits it (see its own javadoc); the one place stored bytes are ever returned is the employee-only `GET /api/v1/kyc/employee/documents/{id}/content` endpoint, gated by `KYC_REVIEW`, which streams the bytes with their real `Content-Type` and never exposes the storage key itself in the response.

A future `S3DocumentStorage` implementing the same interface is a drop-in replacement for `LocalDocumentStorage` — no change to any KYC business logic, controller, or DTO. **AWS/S3 is explicitly not introduced in this phase** — this is documented groundwork for later, not partial infrastructure work now.

## Decision 11 — branch scope: KYC is globally reviewable, not branch-scoped, by explicit decision

ADR-007 (Decision 6) established branch-scoped authorization for cash deposit, anchored on a real existing field, `Account.ifsc`. KYC has no equivalent natural anchor: a `KycApplication` has no account, and inventing a `branchId` column on it — the same mistake ADR-007 explicitly rejected doing to `Account` — would violate this phase's own "do not invent a branch_id unless the domain actually requires it" instruction. The decision: KYC applications are globally reviewable by any employee holding the relevant `KYC_*` permission, with no branch restriction anywhere in kyc-service's authorization logic. `EmployeePrincipal.branchId`/`branchIfsc` are still carried in kyc-service's JWT-derived principal and included in every `KycAuditLog` line, for audit-trail completeness only — never read for an authorization decision.

## Decision 12 — audit: structured logs today, Outbox/Kafka/Audit Service later

`KycAuditLog` (kyc-service, mirroring `EmployeeAuditLog`'s established shape from ADR-006 Decision 7) writes one structured log line per event via a dedicated logger (`com.banksphere.kyc.AUDIT`): `KYC_APPLICATION_SUBMITTED`, `KYC_DOCUMENT_UPLOADED`, `KYC_REVIEW_STARTED`, `KYC_DOCUMENT_VERIFIED`, `KYC_DOCUMENT_REJECTED`, `KYC_ADDITIONAL_INFORMATION_REQUESTED`, `KYC_APPROVED`, `KYC_REJECTED`. Every line carries `employeeId`/`employeeNumber`/`branchId` (null for a customer-initiated event — never fabricated), `customerId`, `applicationId`, `documentId` (where applicable), `result`, `timestamp`, and `correlationId` (from `CorrelationIdFilter`'s MDC entry, the same pattern as every prior service). No Kafka, no Outbox table, no real Audit Service — explicitly deferred, per this phase's own instruction; the event names and field shape are chosen so a future Outbox → Kafka → Audit Service pipeline could adopt them unchanged.

## Decision 13 — future AI and future notifications: documented as extension points, not built

Neither is implemented this phase, per explicit instruction. The intended future shape:

- **AI (document verification assistance)**: Document → OCR/field-extraction → comparison against the customer's own submitted profile fields → a risk/anomaly recommendation surfaced to the KYC officer on the review screen, alongside (never in place of) the real document image and the officer's own judgment. AI would be advisory only — it must never autonomously call `approve`/`reject`; those remain `@PreAuthorize`-gated human actions requiring a real employee JWT, exactly as they are today. The natural integration point is additive: a new, optional field on `KycDocumentResponse`/`KycApplicationDetailResponse` (e.g. `aiRiskSignal`) populated by a future service kyc-service would call, the same "authorized aggregation, never trust the automated signal for the actual decision" shape this ADR already establishes for Customer 360.
- **Notifications**: `KYC_SUBMITTED`, `KYC_ADDITIONAL_INFORMATION_REQUIRED`, `KYC_APPROVED`, `KYC_REJECTED` are exactly the four `KycStatusHistory`/`KycAuditLog` transition points that would need to trigger Email/SMS/WhatsApp/in-app notifications once a Notification Service exists. `KycApplicationServiceImpl.transition` is the single call site where every one of these transitions already happens — a future Notification Service integration would hook in there (via the same Outbox pattern Decision 12 anticipates for audit), not scattered across controllers.

## Consequences

- BankSphere gains a sixth microservice and sixth database, following the established per-service pattern exactly — no shared module, no shared database, no new authentication mechanism.
- `transaction-service` and `beneficiary-service` now each carry their own independent employee-JWT-acceptance code (following ADR-006/007's established "no shared module — every service duplicates its own security code" convention), rather than a shared library — consistent with, not a deviation from, this codebase's existing convention.
- Customer 360 introduces this codebase's first section-level (rather than whole-endpoint) permission model — documented here specifically so a future aggregation endpoint doesn't have to rediscover the same reasoning.
- `KycApplicationDetailResponse` (employee-facing) now also carries `missingDocumentTypes`, originally only on the customer-facing `KycApplicationResponse` — added because both the review screen and Customer 360's KYC section need it, computed by the same `computeMissingDocumentTypes` method either way.

## Deferred to a later phase

AI-assisted document verification (advisory only, never autonomous — Decision 13), a real Notification Service and the four KYC notification triggers (Decision 13), S3-backed document storage (Decision 10), a maker-checker (dual-approval) model for KYC decisions beyond the optimistic-locking conflict protection this phase already provides, Kafka/Outbox for real event publishing (Decision 12), a re-KYC flow for a customer with a prior `APPROVED`/`REJECTED` application, branch-scoped KYC (Decision 11 — not deferred by oversight, deliberately rejected as unnecessary; revisit only if a real multi-branch KYC requirement emerges), loans/cards/forex/service-requests in Customer 360 (Decision 4's `unavailableCapabilities`) — all explicitly out of scope per this phase's own repeated instruction.
