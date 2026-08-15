# Employee Platform

_Status: Phase 9A — employee identity and RBAC foundation only. No operational screens (KYC, loans, cards, cash operations, audit service) exist yet; see [scope.md](../00-project-overview/scope.md#phase-9a-scope--employee-identity-and-rbac-foundation) for the exact boundary and [ADR-006](decisions/ADR-006-employee-identity-and-rbac.md) for the design reasoning behind every decision summarized here._

## Two channels, one bank

BankSphere is now two separate applications against the same fictional institution:

```
Customer Portal (frontend/, :5173)  ──────►  customer-service, account-service,
                                              transaction-service, beneficiary-service

Employee Portal (employee-portal/, :5174) ─► employee-service (:8085)
```

They share nothing at runtime — no code, no build, no session, no signing key, no localStorage key. They share only the same visual design system (`tailwind.config.js` brand/semantic/surface/ink tokens, the same logo) and the same underlying fictional bank identity — see [ADR-006, Decision 1](decisions/ADR-006-employee-identity-and-rbac.md#decision-1--the-employee-portal-is-a-separate-frontend-application-not-a-new-area-of-the-customer-app).

## employee-service

`backend/services/employee-service/`, port **8085**, database `banksphere_employee`. Follows the same conventions as every other backend service (Spring Boot 3.3.5, Java 21, Spring Data JPA, Flyway, Bean Validation, `controller/`/`service/`/`repository/`/`entity/`/`dto/`/`exception/`/`security/`/`config/` package layout) — see [ADR-006, Decision 3](decisions/ADR-006-employee-identity-and-rbac.md#decision-3--employee-identityauthorization-data-lives-in-its-own-service-and-database-no-core-banking-data-is-duplicated) for why it owns no customer/account/transaction data.

### Data model

- **`Branch`** — `id`, `branchCode`, `branchName`, `ifsc`, `status` (`ACTIVE`/`INACTIVE`), timestamps. Deliberately minimal — no branch management endpoints exist yet, just enough for an employee to be assigned to one.
- **`Employee`** — `id`, `employeeNumber` (unique), `username` (unique), `passwordHash` (BCrypt, never plaintext, structurally absent from every response DTO), `firstName`, `lastName`, `email`, `branch` (FK), `status` (`ACTIVE`/`INACTIVE`/`LOCKED`), a many-valued `roles` collection, timestamps.
- **`Role`** (enum) — `TELLER`, `KYC_OFFICER`, `LOAN_OFFICER`, `CARD_OFFICER`, `OPERATIONS`, `BRANCH_MANAGER`, `ADMIN`. An employee may hold more than one (`@ElementCollection` against a fixed enum, backed by an `employee_roles(employee_id, role)` join table — see [ADR-006, Decision 5](decisions/ADR-006-employee-identity-and-rbac.md#decision-5--roles-are-many-to-many-permission-mapping-is-code-defined-not-yet-database-editable)).
- **`Permission`** (enum) — `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `TRANSACTION_VIEW`, `CASH_DEPOSIT`, `CASH_WITHDRAWAL`, `KYC_VIEW`/`KYC_REVIEW`/`KYC_APPROVE`, `LOAN_VIEW`/`LOAN_REVIEW`/`LOAN_APPROVE`, `CARD_VIEW`/`CARD_REVIEW`/`CARD_APPROVE`, `SERVICE_REQUEST_VIEW`/`SERVICE_REQUEST_PROCESS`, `TRANSACTION_INVESTIGATE`, `AUDIT_VIEW`, `EMPLOYEE_MANAGE`, `ROLE_MANAGE`. Defined independently of `Role` on purpose — every authorization check tests a permission, never a role name.

### Role → permission mapping

The authoritative table, `security/RolePermissions` — a fixed, code-defined `Map<Role, Set<Permission>>`, not database-stored (see [ADR-006, Decision 5](decisions/ADR-006-employee-identity-and-rbac.md#decision-5--roles-are-many-to-many-permission-mapping-is-code-defined-not-yet-database-editable)):

| Role | Permissions |
|---|---|
| `TELLER` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `TRANSACTION_VIEW`, `CASH_DEPOSIT`, `CASH_WITHDRAWAL` |
| `KYC_OFFICER` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `KYC_VIEW`, `KYC_REVIEW`, `KYC_APPROVE` |
| `LOAN_OFFICER` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `LOAN_VIEW`, `LOAN_REVIEW`, `LOAN_APPROVE` |
| `CARD_OFFICER` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `CARD_VIEW`, `CARD_REVIEW`, `CARD_APPROVE` |
| `OPERATIONS` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `TRANSACTION_VIEW`, `TRANSACTION_INVESTIGATE`, `SERVICE_REQUEST_VIEW`, `SERVICE_REQUEST_PROCESS` |
| `BRANCH_MANAGER` | `CUSTOMER_VIEW`, `ACCOUNT_VIEW`, `TRANSACTION_VIEW`, `CASH_DEPOSIT`, `CASH_WITHDRAWAL`, `KYC_VIEW`, `KYC_REVIEW`, `LOAN_VIEW`, `LOAN_REVIEW`, `CARD_VIEW`, `CARD_REVIEW`, `SERVICE_REQUEST_VIEW`, `SERVICE_REQUEST_PROCESS`, `TRANSACTION_INVESTIGATE`, `AUDIT_VIEW` — broad oversight, deliberately **not** the specialist `*_APPROVE` permissions |
| `ADMIN` | `EMPLOYEE_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`, `CUSTOMER_VIEW`, `ACCOUNT_VIEW` — deliberately **not** `CASH_DEPOSIT`/`CASH_WITHDRAWAL`/any `*_APPROVE` |

An employee's effective permission set is the union of every role they hold (`RolePermissions.permissionsFor(Set<Role>)`), computed once at token-issue time and embedded directly in the JWT.

### JWT model

Employee tokens are signed with **`EMPLOYEE_JWT_SECRET`** — a completely separate HS256 key from customer-service's `JWT_SECRET`. This is the central design decision of this phase; the full reasoning (why claim-based distinction alone was rejected) is in [ADR-006, Decision 2](decisions/ADR-006-employee-identity-and-rbac.md#decision-2--employee-jwts-are-signed-with-a-separate-key-from-customer-jwts-not-just-a-separate-claim). Claims carried:

| Claim | Meaning |
|---|---|
| `sub` | Employee's UUID |
| `principalType` | Always `"EMPLOYEE"` — a defense-in-depth marker, checked by `JwtAuthenticationFilter` in addition to (never instead of) the signature check |
| `employeeNumber` | e.g. `EMP000123` |
| `roles` | e.g. `["TELLER"]` |
| `permissions` | The flattened effective permission set at issue time, e.g. `["CUSTOMER_VIEW", "ACCOUNT_VIEW", ...]` |
| `branchId` | The employee's assigned branch — not yet enforced against by any endpoint, but present for the future branch-scoped authorization step (see below) |
| `iat` / `exp` | Standard; default lifetime **30 minutes** (`EMPLOYEE_JWT_EXPIRATION`, shorter than a customer session's 1 hour, given the higher-privilege operational context) |

## Authentication

`POST /api/v1/employees/auth/login` — the one public endpoint in this service. Request: `{ username, password }`. Response: `{ accessToken, tokenType, expiresIn, employee, roles, permissions, branch }`. A wrong password, an unknown username, an `INACTIVE` employee, and a `LOCKED` employee all fail identically (`401`, "Invalid username or password") — the same account-enumeration defense customer-service's login uses (a timing-safe dummy BCrypt hash so an unknown username doesn't respond measurably faster), applied here even though employee registration is admin-only, not public.

`GET /api/v1/employees/me` — any authenticated employee may view their own profile. Returns the full `EmployeeResponse` (identity, roles, permissions, branch, status, timestamps) — structurally no `passwordHash` field anywhere in the type.

## Authorization (RBAC)

Enforced server-side via `@PreAuthorize("hasAuthority('SOME_PERMISSION')")` on `EmployeeController`'s admin endpoints (`SecurityConfig` enables `@EnableMethodSecurity` specifically for this — the one deliberate addition versus every other service's `SecurityConfig`). See [ADR-006, Decision 4](decisions/ADR-006-employee-identity-and-rbac.md#decision-4--rbac-is-enforced-with-preauthorize-against-permissions-server-side-on-every-endpoint-that-needs-it--never-by-hiding-a-nav-item) for the full reasoning, including a real bug (an `AccessDeniedException` silently swallowed by a generic exception handler, turning intended 403s into 500s) found and fixed while building this.

The employee-portal frontend's `RequirePermission` route guard and permission-filtered "Your access" list are UX conveniences only — the backend's `@PreAuthorize` checks are authoritative and were proven independently (via `EmployeeControllerTest`'s parameterized per-role test, and live via `curl` against a running instance — see the Phase 9A engineering journal entry).

## Employee administration

No public employee registration. `POST /api/v1/employees`, `GET /api/v1/employees`, `GET /api/v1/employees/{id}`, and `PUT /api/v1/employees/{id}/status` all require `EMPLOYEE_MANAGE` (held only by `ADMIN` in the current mapping). A single bootstrap `ADMIN` employee (`admin` / a rotatable local-dev-only password) is seeded via Flyway migration to close the chicken-and-egg problem of "nothing can create the first admin" — see [ADR-006, Decision 6](decisions/ADR-006-employee-identity-and-rbac.md#decision-6--no-public-employee-registration-a-single-seeded-bootstrap-admin-closes-the-chicken-and-egg-problem).

## Branch model

Deliberately minimal this phase: `Branch` exists, `Employee.branch` is required and appears on the JWT/profile, but **no endpoint filters or restricts anything by branch yet**. A `BRANCH_MANAGER` can currently see/manage the same scope any other `EMPLOYEE_MANAGE`-holding caller could (which in practice is none — only `ADMIN` holds `EMPLOYEE_MANAGE` today). Real branch-scoped authorization (an employee's operations restricted to their own branch unless their role explicitly grants broader scope) is the documented next security step — see [ADR-006, Decision 8](decisions/ADR-006-employee-identity-and-rbac.md#decision-8--branch-is-minimal-on-purpose-branch-scoped-authorization-is-a-documented-future-step-not-a-present-one). The `branchId` JWT claim exists today specifically so that future logic has it available without an extra lookup.

## Employee Portal (`employee-portal/`)

Separate Vite + React + TypeScript + Tailwind application, port 5174, following the same technology/testing conventions as the customer frontend (Vitest + React Testing Library, same `apiClient`/`tokenStorage`/`AuthContext`/`ProtectedRoute` shape) but with its own codebase, its own `localStorage` key (`banksphere.employee.auth`), and its own component subset (only the genuinely domain-agnostic pieces — `Button`, `Card`, `Input`, `Badge`, `Spinner`, `ErrorState`, `Icon` — were reused; nothing customer-domain-specific was pulled in).

Built this phase: **Login** (`pages/auth/Login.tsx`), an authenticated **shell** (`layouts/AppLayout.tsx` — header with employee name/number and a logout button, a permission-aware sidebar), **role-aware navigation** (the sidebar's "Your access" list is filtered live against `AuthContext.hasPermission`, sourced from `data/navigationCatalog.ts` — every entry is clearly labeled "Coming soon" rather than linking to a page that doesn't exist yet, since no operational screens are built this phase), the **employee profile** page (`pages/profile/Profile.tsx` — name, employee number, username, email, status, branch, roles, and effective permissions, fetched live from `GET /api/v1/employees/me`), **logout**, and an **unauthorized** page (`pages/errors/Unauthorized.tsx`, reached via `RequirePermission`).

Not built: any operational screen. `RequirePermission` and the nav's permission filtering exist and are exercised by tests, but nothing routes to a real KYC/loan/card/cash-operations page yet — there isn't one.

## Audit preparation

Every login attempt (success or failure) and every employee-management action (create, status change) is written as one structured log line via `service/EmployeeAuditLog`, to a dedicated `com.banksphere.employee.AUDIT` logger — `employeeId`, `employeeNumber`, `action`, `result`, `timestamp`, `correlationId`. A new `security/CorrelationIdFilter` attaches a correlation id to every request (from the caller's own `X-Correlation-Id` header if present, else freshly generated) and echoes it back in the response header, so a caller and the audit trail can be correlated. This is explicitly not the Audit Service named in the project roadmap — see [ADR-006, Decision 7](decisions/ADR-006-employee-identity-and-rbac.md#decision-7--audit-ready-structured-logging-now-a-real-audit-service-later) for what it is instead and what a future phase still needs to build.

## Future operations integration

When operational endpoints (KYC review, loan/card approval, cash deposit/withdrawal, service-request processing, transaction investigation) are eventually built, they call the existing core-banking services directly — never employee-service's own database, which owns no banking data:

```
Employee Portal → Employee-facing API (new endpoints, gated by @PreAuthorize
                   against the Permission enum already defined here)
               → account-service / transaction-service / customer-service
               → their own databases
```

See [ADR-006, Decision 3](decisions/ADR-006-employee-identity-and-rbac.md#decision-3--employee-identityauthorization-data-lives-in-its-own-service-and-database-no-core-banking-data-is-duplicated) and [scope.md](../00-project-overview/scope.md) for what's explicitly still out of scope (Kafka, an Outbox pattern, ATM/cash operations, KYC, loans, cards, a Payment Switch, a real Audit Service, Kubernetes).

## Future audit integration

`EmployeeAuditLog`'s structured lines are designed to be shipped, unmodified, to a future log aggregation stack (Fluent Bit/OpenSearch, per the project roadmap) or consumed directly by a future Kafka-backed Audit Service — no schema change to this service should be needed when that phase arrives; it's a matter of a shipper/consumer being pointed at these existing lines.
