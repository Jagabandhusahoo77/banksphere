# 2026-08-13 — Phase 9A: Employee Identity, RBAC, and Platform Foundation

## Objective

Expand BankSphere from a single-channel customer product into a two-channel platform: the existing customer portal, plus a new, separate employee/bank-operations portal — starting with only the identity and authorization foundation (a new `employee-service`, a new `employee-portal/` frontend, roles, permissions, employee JWTs). Explicitly no KYC, loans, cards, cash operations, ATM, audit service, or Kafka this phase.

## The central design question: share the JWT secret, or not?

The task's own instructions posed this directly and refused to let it be waved away: *"Do not simply add EMPLOYEE to the existing customer role list and call the problem solved. If sharing the JWT secret between services creates a security problem, document the issue rather than silently accepting it."*

Considered sharing `JWT_SECRET` and distinguishing an employee token from a customer token purely by claim shape (a `principalType` claim, or role name). Rejected: that design means the *only* thing preventing a customer's already-valid JWT from authenticating as an employee is every current and future employee endpoint remembering to check the right claim. One missed check on one future endpoint and a customer session becomes a valid employee credential. That's a real, describable failure mode, not a theoretical one — so it wasn't accepted.

Built instead: employee-service reads a wholly separate signing key, `EMPLOYEE_JWT_SECRET` (own env var, own ≥32-byte fail-fast check, same pattern CLAUDE.md already mandates for `JWT_SECRET`). A customer JWT fails signature verification at employee-service before any claim is ever inspected — cryptographic separation, not claim-based hoping. A `principalType: "EMPLOYEE"` claim and an `isEmployeeToken()` check were added on top as a second, independent layer, but explicitly documented (in `JwtService`'s own javadoc and in [ADR-006](../architecture/decisions/ADR-006-employee-identity-and-rbac.md)) as defense-in-depth, not the primary defense.

This was proven three ways, not just designed:
1. **Unit test**: `JwtServiceTest.parseClaims_returnsEmpty_forTokenSignedWithADifferentKey_modelingACustomerJwt` — builds a real token with a real different key, confirms rejection.
2. **Live HTTP proof**: registered a real customer via the real running customer-service, obtained a real customer JWT, presented it to the real running employee-service's `/api/v1/employees/me` — got a genuine `401`, not a simulated one.
3. **Real browser**: never applicable here (a browser never holds both tokens at once in this architecture — the two portals don't share storage), but the portal's own `localStorage` key (`banksphere.employee.auth`) was confirmed distinct from the customer portal's (`banksphere.auth`) during the E2E run below.

## RBAC model

`Role` (7 fixed values) and `Permission` (20 fixed values) are separate enums; `security/RolePermissions` is a single, code-defined `Map<Role, Set<Permission>>` — the authoritative table, documented in full in [docs/architecture/employee-platform.md](../architecture/employee-platform.md). Two roles (`BRANCH_MANAGER`, `ADMIN`) weren't fully specified by the task's own examples; filled in with a documented rationale (branch managers get broad oversight but not specialist approval authority; admins get employee/role administration but explicitly not money-moving or approval permissions, per the task's own caution against over-privileging admin).

An employee's effective permission set is computed once at login (union of every role's permissions) and embedded directly in the JWT's `permissions` claim — every downstream authorization check reads straight off the token, no per-request database round-trip.

Enforcement: `EmployeeController`'s admin endpoints are the first `@PreAuthorize`-annotated methods in this codebase (`SecurityConfig` gained `@EnableMethodSecurity` specifically for this). RBAC is never implemented by hiding a UI element — the employee-portal's own `RequirePermission` route guard and permission-filtered nav are both explicitly commented as UX conveniences that mirror, never replace, the backend check.

## A real bug found while writing the RBAC tests

Writing `EmployeeControllerTest`'s parameterized "every non-ADMIN role gets 403" test, every case failed with `500`, not `403`. Traced it: `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` — the exact same boilerplate block every other service already has, copied verbatim when scaffolding this service — was catching Spring Security's `AccessDeniedException` (thrown by `@PreAuthorize`) before `JwtAccessDeniedHandler` (the class actually responsible for turning that into a proper 403 response) ever got a chance to run. No prior service in this codebase uses `@PreAuthorize`, so this exact interaction had never had an opportunity to surface before — it's the same *class* of problem CLAUDE.md already documents for `@AutoConfigureMockMvc(addFilters = false)`: an unrelated piece of otherwise-correct infrastructure quietly defeating a specific security mechanism. Fixed with an explicit `@ExceptionHandler(AccessDeniedException.class)` returning 403, placed and documented in `GlobalExceptionHandler` itself so the next service scaffolded from this one inherits the fix rather than the bug.

## Bootstrapping without public registration

`POST /api/v1/employees` requires `EMPLOYEE_MANAGE`, held only by `ADMIN`. With no public registration, nothing could create the first employee — so `V2__seed_bootstrap_branch_and_admin.sql` seeds one branch (`HQ001`, IFSC `BANK0000001`, matching account-service's own constant) and one `ADMIN` employee. The password hash is a real BCrypt hash, generated out-of-band (Flyway migrations can't call application code) via a throwaway Java snippet run inside the project's own `maven:3.9-eclipse-temurin-21` container using the exact `BCryptPasswordEncoder` this service uses at runtime, then round-trip-verified (`encoder.matches(...)` returned `true`) before being committed as a migration literal — not just pasted and hoped correct.

## Audit preparation

Every login (success/failure) and employee-management action is written as one structured log line (`EmployeeAuditLog`, a dedicated `com.banksphere.employee.AUDIT` logger) carrying `employeeId`/`employeeNumber`/`action`/`result`/`timestamp`/`correlationId`. A new `CorrelationIdFilter` attaches a correlation id to every request (from the caller's `X-Correlation-Id` header if present, else generated) via SLF4J MDC, echoed back in the response header. Deliberately not a database table, not Kafka, not the full Audit Service — the minimum shape a future log-shipper or Audit Service could consume without reparsing free text. A failed login's audit line omits `employeeId`/`employeeNumber` for an unknown username (only the attempted username is logged) so the audit trail itself can't become a username-enumeration oracle.

## employee-portal

New Vite/React/TypeScript/Tailwind application (port 5174), same conventions as the customer frontend (Vitest + RTL, `apiClient`/`tokenStorage`/`AuthContext`/`ProtectedRoute` shapes) but its own codebase and its own `localStorage` key. Only genuinely domain-agnostic components were reused (`Button`, `Card`, `Input`, `Badge`, `Spinner`, `ErrorState`, `Icon`) — nothing customer-domain-specific. Built: Login, an authenticated shell (`AppLayout` — employee name/number, logout), permission-aware navigation (a "Your access" list, filtered live against the employee's real permissions, each entry explicitly labeled "Coming soon" rather than linking anywhere — no operational screens exist yet, and nothing pretends otherwise), the employee profile page (fetched live from `GET /api/v1/employees/me`, showing branch/roles/permissions), logout, and an unauthorized page (reached via `RequirePermission`).

## Tests

**Backend** (`employee-service`, 57 tests): `JwtServiceTest` (7 — token round-trip, multi-role permission union, malformed/expired/wrong-key rejection, the cross-service-modeling test), `RolePermissionsTest` (9 — pins the exact table per role), `EmployeeServiceImplTest` (12 — creation, duplicate employee number/username, password hashing verified via captured argument, role assignment, branch-not-found, list/get/updateStatus), `EmployeeAuthServiceImplTest` (9 — login success, wrong password, unknown username, inactive, locked, `/me` success/not-found, a structural reflection check that `EmployeeResponse` has no field capable of carrying a password), `EmployeeAuthControllerTest` (8 — login public, 401 paths, `/me` 200/401, malformed and non-employee-claim token rejection), `EmployeeControllerTest` (14 — 401/201 baseline, a parameterized test rejecting every non-`ADMIN` role from `createEmployee`, list/get/updateStatus 200-for-admin/403-for-non-admin, and the "non-admin can't view a colleague's protected profile" test).

**Frontend** (`employee-portal`, 15 tests): `AuthContext.test.tsx` (4 — unauthenticated start, login stores employee, permission reflects the real granted set not a role-name assumption, logout clears), `ProtectedRoute.test.tsx` (2), `RequirePermission.test.tsx` (2), `Login.test.tsx` (4 — submit gating, successful navigation, generic-error display, already-authenticated redirect), `Profile.test.tsx` (3 — real data displayed, no password/hash text anywhere, error state on failure).

## Build and test results

- `mvn clean verify` (`employee-service`, Docker `maven:3.9-eclipse-temurin-21`): **BUILD SUCCESS**, 57/57 passing.
- Regression: `customer-service` (35), `account-service` (62), `transaction-service` (12), `beneficiary-service` (25) all re-run — **134/134 passing, zero regressions**, none of these four services' code was touched this phase.
- `employee-portal`: `npm run build` clean; `npm test` — 15/15 passing.
- `frontend` (customer portal) regression: `npm run build` clean; `npm test` — 37/37 passing, untouched this phase.

## Real end-to-end verification

Rebuilt the actual `employee-service` Docker image and ran it against the project's live shared Postgres container (which does not yet have `banksphere_employee` from a fresh volume-init, since that container was created in an earlier phase before this one added the database to `docker/postgres/init/001-create-databases.sh` — created it manually this once, exactly the situation a fresh `docker compose up` would handle automatically going forward). Flyway applied both migrations cleanly against the real database. Then, via `curl` against the real running service: logged in as the seeded bootstrap admin and confirmed the JWT's claims and the response's `permissions` list matched `RolePermissions.permissionsFor(ADMIN)` exactly; confirmed wrong-password and unknown-username both return `401` with identical messages; used the admin token to provision a real `TELLER` employee; logged in as that teller and confirmed their JWT carries exactly the `TELLER` permission set; confirmed the teller's attempt to call the admin-only `createEmployee` endpoint returns `403`; confirmed `/me` for both employees returns no password field; and — the core proof — registered a real customer against the real running `customer-service`, obtained a real customer JWT, and confirmed presenting it to employee-service's `/me` returns a genuine `401`. Captured the service's own audit log lines during this run and confirmed every login/creation event appeared with the expected structured fields and a real correlation id.

Separately, with real browser tooling (`google-chrome` via `puppeteer-core`, same method established in Phase 8A): served the actual compiled `employee-portal` via its own Vite dev server pointed at the live employee-service, and drove a real login → profile → logout journey. Confirmed: redirect to `/login` when unauthenticated, successful form submission, redirect to `/` → `/profile`, the profile page correctly rendering the teller's name/employee number/roles/branch/permissions (fetched live from `/me`, not reused from the login response), no "password" text anywhere on the page, the correct portal-specific `localStorage` key (`banksphere.employee.auth`), zero console errors, zero page errors, and a clean logout back to `/login`.

## What was deliberately not done

Any operational employee-facing endpoint (KYC, loans, cards, cash deposit/withdrawal, service requests, transaction investigation), branch-scoped authorization enforcement (the branch id flows through the token/profile today specifically so a future phase has it without a second lookup), a database-backed/admin-editable role→permission mapping (`ROLE_MANAGE` exists as a permission name for this future capability, not yet a working feature), the real Audit Service, Kafka, an Outbox, an ATM system, Kubernetes — all explicitly out of scope per the task's own repeated instruction.
