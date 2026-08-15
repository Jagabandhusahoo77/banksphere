# ADR-003: Beneficiary Service

**Status:** Accepted for Phase 6 (Microservices) — beneficiary-service

## Context

BankSphere's roadmap always planned a `beneficiary-service` (see `docs/00-project-overview/roadmap.md`'s Phase 6 row and the original `backend/services/beneficiary-service` scaffold directory, empty until this phase). Its job: let a customer save the people/accounts they send money to, so a future transfer flow doesn't require re-entering account details every time. This phase implements beneficiary management only — no transfer, no payment switch, no interbank rails, no Kafka, no notification integration. Those are explicitly later phases (see the roadmap's own dependency chain: Beneficiary Service → Payment/Transfer Service → Payment Switch → Other Bank).

## Decision 1 — Duplicate the existing per-service infrastructure pattern, not `backend/shared/`

`backend/shared/{common-models,common-exceptions,common-utils}` exist as directories in this repository but are **completely empty and unused by every existing service** — customer-service, account-service, and transaction-service each independently duplicate their own `JwtValidator`/`SecurityConfig`/`ErrorResponse`/`GlobalExceptionHandler`, not a shared library. This was confirmed by inspection before writing any code, per this phase's own instructions to inspect first.

**Decision:** beneficiary-service follows the same duplication pattern — its own copy of the JWT-validation security package, its own `ErrorResponse`/`GlobalExceptionHandler`, its own DTOs. Adopting `backend/shared/` now, for one service only, would create an inconsistent codebase (three services duplicating, one depending on a shared library) rather than a more consistent one, and would require retrofitting the three existing services to match — explicitly out of scope ("do not modify other services unnecessarily"). If `backend/shared/` is ever adopted, it should be adopted for all services in one deliberate pass, not introduced piecemeal by whichever service happens to be built next.

## Decision 2 — Soft delete (status → `INACTIVE`), not a hard `DELETE FROM`

Neither customer-service nor account-service ever performs a hard SQL delete anywhere in the existing codebase — both model lifecycle as a status transition (`CustomerStatus.INACTIVE`/`SUSPENDED`, `AccountStatus.INACTIVE`/`CLOSED`) rather than row removal. `DELETE /api/v1/beneficiaries/{id}` extends that same existing convention: it sets `status = INACTIVE` and returns `204`, never issuing a `DELETE FROM beneficiaries`. This is also just safer for a banking application — an accidentally "deleted" beneficiary is one `PUT`-adjacent bug away from being unrecoverable if hard-deleted, whereas a soft-deactivated one can be reasoned about, audited, or (in a later phase) reactivated.

**Consequence:** `GET /api/v1/beneficiaries` (the list endpoint) filters to `ACTIVE` only — deactivated beneficiaries don't clutter the "who can I send money to" list, which is the endpoint's actual job. `GET /api/v1/beneficiaries/{id}` (direct lookup by id) does **not** filter by status — the owner can still look up a beneficiary they deactivated; ownership, not status, is what gates that endpoint. This split is deliberate, not an inconsistency: list vs. direct-lookup have different jobs.

## Decision 3 — Duplicate prevention scoped to `ACTIVE` rows via a partial unique index

The stated rule — "same customer + same account number + same IFSC shouldn't produce duplicate active beneficiaries" — is enforced two ways, per the task's own instruction not to rely on the application layer alone (concurrent requests can race past an in-process check):

1. **Application layer**: `BeneficiaryServiceImpl.createBeneficiary` checks `existsByCustomerIdAndAccountNumberAndIfscAndStatus(..., ACTIVE)` before inserting, throwing `DuplicateBeneficiaryException` (409) with a specific, actionable message.
2. **Database layer**: `uq_beneficiaries_customer_account_ifsc_active`, a **partial** unique index (`WHERE status = 'ACTIVE'`) on `(customer_id, account_number, ifsc)` — see the V1 migration and `docs/database/README.md`. A `DataIntegrityViolationException` from a constraint hit (the concurrent-race case) is caught generically by `GlobalExceptionHandler` and mapped to the same `409`.

**Why partial, not a plain unique constraint:** a plain (non-partial) unique constraint on `(customer_id, account_number, ifsc)` would permanently block re-adding a beneficiary after deactivating it — the exact account/IFSC combination would be unique forever, active or not. Scoping the index to `WHERE status = 'ACTIVE'` means deactivating a beneficiary genuinely frees up that combination, matching how "delete and re-add" is expected to behave (see Decision 4).

## Decision 4 — `PUT` cannot change `accountNumber`/`ifsc`

`UpdateBeneficiaryRequest` only accepts `beneficiaryName`, `bankName`, `nickname`. Allowing an in-place edit of the account number or IFSC would mean "updating" a beneficiary could silently repoint it to a completely different real-world account — and would let a customer route around Decision 3's duplicate check entirely (create beneficiary A, then "update" it to have the same account/IFSC as an already-existing active beneficiary B, bypassing the create-time check). The existing pattern most real bank "manage payee" UIs already use — delete and re-add rather than edit the identifying fields — is adopted here for the same reason.

## Decision 5 — No outbound calls to other services (yet)

Unlike account-service (which calls transaction-service to record ledger entries) or transaction-service (which calls account-service to verify ownership), beneficiary-service makes **no** outbound HTTP calls in this phase. Its `CurrentUser` security helper accordingly has no `bearerToken()` method (account-service/transaction-service's does, specifically to forward the caller's token onward) — there's nothing to forward a token to yet. When Transfer/Payment Service is built, it will most likely call *into* beneficiary-service (to validate a beneficiary exists and is active before initiating a transfer) rather than the reverse.

## Consequences

- `GET /api/v1/beneficiaries` and `GET /api/v1/beneficiaries/{id}` never expose another customer's beneficiaries — ownership is re-derived from the JWT on every request, never trusted from the URL, matching every other service's authorization model (see `docs/security/authorization.md`).
- A future Transfer/Payment Service can treat "beneficiary exists, is `ACTIVE`, and is owned by the initiating customer" as a precondition it checks against this service's `GET /api/v1/beneficiaries/{id}`, the same cross-service ownership-verification pattern transaction-service already uses against account-service.
- No Kafka topic, no event emission on beneficiary create/update/deactivate — those become relevant once a consumer (e.g. a future audit-service or notification-service) actually exists to read them. Emitting events with no consumer would be unverifiable, speculative infrastructure.

## Do NOT implement now

Transfer/payment initiation, Kafka event publishing, interbank rails, a payment switch, or notification-service integration. This ADR and this phase cover beneficiary lifecycle management only.
