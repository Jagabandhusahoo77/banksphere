# 2026-08-13 — Phase 6: `beneficiary-service`

## Objective

Build the first service of the "Microservices" roadmap phase: `beneficiary-service`, letting a customer save the people/accounts they'll later send money to. Explicitly scoped to beneficiary lifecycle management only — no money movement, no Kafka, no interbank rails, no payment switch, no notification-service integration, no frontend integration. Those are later, separate milestones (see the roadmap's own dependency chain: Beneficiary Service → Payment/Transfer Service → Payment Switch → Other Bank).

## Inspection before implementation

Per the task's own instruction, the existing codebase was inspected before writing anything: `account-service`'s `pom.xml`, `application.yml`, entity/enum, exception package, full security package, controller/service/repository, DTOs, config, Dockerfile, tests, `docker-compose.yml`, the Postgres init script, and the relevant docs. The goal was byte-for-byte pattern consistency, not a fresh design.

**Key discovery:** `backend/shared/{common-models,common-exceptions,common-utils}` exist as directories in this repository but are completely empty and unused by any of the three existing services — each duplicates its own `JwtValidator`/`SecurityConfig`/`ErrorResponse`/`GlobalExceptionHandler` independently. This resolved the task's "reuse common-model/common-exceptions/common-utils where appropriate" instruction: the appropriate action was to *not* introduce shared-library usage now, since doing so for one service only would create a more inconsistent codebase, not a less inconsistent one, and retrofitting the three existing services was explicitly out of scope. See [ADR-003, Decision 1](../architecture/decisions/ADR-003-beneficiary-service.md#decision-1--duplicate-the-existing-per-service-infrastructure-pattern-not-backendshared).

## What was built

- A fourth independent Spring Boot service (port 8084) matching the other three exactly: Java 21, Spring Web/Data JPA/Validation/Security/Actuator, jjwt 0.12.6, its own `banksphere_beneficiary` PostgreSQL database, Flyway `V1__create_beneficiaries_table.sql` (no prior migrations existed, so no numbering deviation was needed, unlike Phase 3A's customer-service V4-vs-V1 mismatch).
- Full CRUD: `POST/GET /api/v1/beneficiaries`, `GET/PUT/DELETE /api/v1/beneficiaries/{id}`. The owning customer is always derived from `Authentication` via a `CurrentUser.id(...)` helper — never accepted as a client-supplied `customerId` on any request, matching the Phase 3A `AccountCreateRequest` precedent. `POST /beneficiaries?customerId=anotherCustomer` has no effect; the query parameter is simply not read.
- Ownership enforcement: `GET/PUT/DELETE /{id}` look the row up first, then compare `beneficiary.customerId` against the caller's own id, returning `404` if the row doesn't exist and `403` if it exists but belongs to someone else — the same check-after-lookup ordering account-service already uses for its own opaque UUID resource ids (not enumeration-sensitive, unlike a login endpoint).
- Duplicate prevention (same customer + account number + IFSC shouldn't produce two *active* beneficiaries), enforced at two layers per the task's explicit instruction not to rely on the application layer alone:
  1. Application layer: `BeneficiaryServiceImpl.createBeneficiary` checks `existsByCustomerIdAndAccountNumberAndIfscAndStatus(..., ACTIVE)` before inserting, throwing `DuplicateBeneficiaryException` → `409`.
  2. Database layer: `uq_beneficiaries_customer_account_ifsc_active`, a **partial** unique index (`WHERE status = 'ACTIVE'`) on `(customer_id, account_number, ifsc)`. A constraint hit from a genuine concurrent race surfaces as `DataIntegrityViolationException`, caught generically by `GlobalExceptionHandler` and mapped to the same `409` — the same backstop pattern the Phase 1 review added to the other three services.
  Partial, not a plain unique constraint, specifically so deactivating a beneficiary frees up its account/IFSC combination for re-adding later (see ADR-003, Decision 3).
- Soft-delete lifecycle (`ACTIVE`/`INACTIVE`): `DELETE /{id}` sets `status = INACTIVE` and returns `204`, never issuing a hard `DELETE FROM` — matching the existing `CustomerStatus`/`AccountStatus` convention (no hard delete exists anywhere else in this codebase). `GET /beneficiaries` (list) filters to `ACTIVE` only; `GET /beneficiaries/{id}` (direct lookup) does not filter by status, since ownership — not status — is what gates that endpoint. See ADR-003, Decision 2.
- Validation: Jakarta Bean Validation on `CreateBeneficiaryRequest` — beneficiary name/bank name/nickname not-blank with reasonable max lengths, account number `^[0-9]{9,18}$`, IFSC `^[A-Z]{4}0[A-Z0-9]{6}$` (standard 11-character Indian IFSC structure). `UpdateBeneficiaryRequest` deliberately excludes `accountNumber`/`ifsc` — allowing an in-place edit of the identifying fields would both silently repoint a beneficiary at a different real-world account and let a customer route around the duplicate check (create A, then "update" A to collide with existing active B). See ADR-003, Decision 4.
- Logging: beneficiary created/updated/deactivated/duplicate-attempt events logged at INFO with a masked account number (`"****" + last 4 digits`) — never the raw account number, and never any JWT/password/credential material, consistent with the project's logging rules.
- 25 tests: 11 `BeneficiaryServiceImplTest` (Mockito) covering create success/invalid-data/duplicate, get-list/get-one/get-nonexistent/get-cross-customer-denied, update success/unauthorized, deactivate, and both invalid-IFSC/invalid-account-number validation cases; 14 `BeneficiaryControllerTest` (`@WebMvcTest`) covering the same behavior at the HTTP layer plus the 401/403/404/409/400 status codes explicitly. Applied the Phase 3A lesson from the start this time: `@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})` rather than `addFilters = false`, since a plain `Authentication` controller parameter needs the real filter chain to resolve `HttpServletRequest.getUserPrincipal()` — `mvn verify` passed with `BUILD SUCCESS` and 25/25 tests green on the first attempt.
- Docker/Compose wiring: new `beneficiary-service` block in `docker-compose.yml` (port 8084 via `BENEFICIARY_SERVICE_PORT`), `CREATE DATABASE banksphere_beneficiary` added to the Postgres init script, `.env.example` updated. The `frontend` service block was deliberately left untouched — an initial pass added a `VITE_BENEFICIARY_SERVICE_URL` build arg and a `depends_on` entry, which was self-reverted on review since frontend integration is explicitly out of scope for this phase and the task's own instruction says not to modify other services/components unnecessarily.

## Live smoke test

Registered two real customers via the running customer-service, then exercised every beneficiary-service endpoint across both: create (201), create with invalid IFSC/account number/blank name (400 ×3), create duplicate active beneficiary (409), list (200, ACTIVE only), get own beneficiary (200), get nonexistent id (404), get the other customer's beneficiary by id (403), no token (401), update own beneficiary (200), update the other customer's beneficiary (403), deactivate (204), confirm it drops out of the list but is still fetchable by id, then re-created the identical account/IFSC combination successfully (confirming the partial-index re-add behavior). Every result matched the design.

## Build verification

`mvn clean test` and `mvn verify` both run inside the `maven:3.9-eclipse-temurin-21` Docker container (no local Java/Maven available in this environment, consistent with every prior backend phase) — `BUILD SUCCESS`, 25/25 tests passing, on the first attempt.

## What was deliberately not done

- No frontend integration (`beneficiaryService.ts`, any UI) — backend-only phase, per the task's explicit scope.
- No Kafka event emission on create/update/deactivate — no consumer exists yet to read such events; adding one now would be speculative, unverifiable infrastructure (ADR-003 Consequences).
- No outbound HTTP calls from beneficiary-service to any other service — unlike account-service/transaction-service, there's nothing to call yet. `CurrentUser` accordingly has no `bearerToken()` method (ADR-003, Decision 5).
- No `backend/shared/` adoption — see "Inspection before implementation" above and ADR-003, Decision 1.
- Transfer Service, Payment Switch, Notification Service, api-gateway, and Kubernetes were explicitly not started, per the task's twice-stated instruction to stop after this milestone.

## Environment constraints

Same as every prior backend phase: no local Java/Maven (Docker Maven container used for all builds/tests), no `docker compose` binary (manual `docker build`/`docker run` on the shared `banksphere-net` network used instead), no browser (not applicable — this phase shipped no frontend code).
