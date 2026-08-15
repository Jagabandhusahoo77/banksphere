# 2026-08-12 — Phase 3A: Authentication & Authorization

## Phase 3A objective

Replace Phase 1/2's placeholder "login" (typing in any existing customer's UUID, with no password and no check that the caller had any relationship to the data they then accessed) with real authentication and authorization:

- Registration, login, logout via `customer-service` (not a new `auth-service` — see [ADR-002](../architecture/decisions/ADR-002-authentication.md)).
- BCrypt password hashing; JWT-based sessions with a configurable signing secret, never hardcoded.
- Protected APIs across all three existing backend services, and protected frontend routes.
- **The core requirement:** account/customer ownership validation, so a signed-in customer can never read or modify another customer's data by changing an ID in the URL or request body.
- Correct `401` (unauthenticated) vs. `403` (unauthorized) semantics everywhere, and no account-enumeration via login error messages.
- Explicitly out of scope: a separate `auth-service`, Kafka, Redis, Kubernetes, AWS/Terraform, CI/CD, Argo CD, Prometheus/Grafana, React Native, and money transfer itself (reserved for a follow-on "Phase 3B").

## Work completed

**customer-service:** `V2__add_authentication.sql` (a `customer_credentials` table, keyed by `customer_id`); `JwtProperties`/`JwtService` (issues *and* validates — the only service that does both); `JwtAuthenticationFilter`/`JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler`/`SecurityConfig`/`CurrentUser`; `AuthController` (`register`/`login`/`logout`/`me`) and `AuthServiceImpl` (timing-safe, non-enumerating login — see [docs/security/authentication.md](../security/authentication.md)); ownership checks added to the existing `CustomerController`/`CustomerServiceImpl`.

**account-service:** validate-only `JwtValidator` (no `generateToken`) plus the same filter/entry-point/access-denied/config quartet; `AccountCreateRequest.customerId` removed entirely (breaking change — the owner is always the token's subject now); ownership checks added to every method in `AccountServiceImpl`; the caller's bearer token is now forwarded to transaction-service on every deposit/withdraw/create-with-initial-deposit call.

**transaction-service:** the same JWT-validation quartet; a new `AccountOwnershipClient`/`RestAccountOwnershipClient` that calls back to account-service's own `GET /accounts/{id}` (forwarding the caller's token) to verify ownership before returning transaction data, failing closed on any non-2xx response, timeout, or connection error; `POST /api/v1/transactions` now requires authentication (closing the Phase 1 gap where it had none) but deliberately does not re-check ownership — see [ADR-002, Decision 5](../architecture/decisions/ADR-002-authentication.md#decision-5--post-apiv1transactions-requires-authentication-but-does-not-re-verify-account-ownership).

**Frontend:** `types/auth.ts`, `services/tokenStorage.ts` (localStorage + an `AUTH_CLEARED_EVENT` window event), `services/authService.ts`; `apiClient.ts` gained a request interceptor (attach `Authorization: Bearer <token>`) and a response interceptor (clear stored auth on any `401`); `AuthContext` rewritten around a real `customer`/`isAuthenticated` shape (keeping a derived `customerId` so `useCustomer(customerId)`/`useAccounts(customerId)` call sites didn't need to change); `Login.tsx` rewritten for email+password; new `Register.tsx`; `ProtectedRoute` updated to check `isAuthenticated`; a "Transfer (Coming Soon)" nav entry, `/transfer` route, and dashboard quick action — money transfer itself is Phase 3B, not built here.

**Docs:** this entry, `docs/security/{authentication,authorization,jwt,threat-model}.md`, [ADR-002](../architecture/decisions/ADR-002-authentication.md), and updates to `docs/api/README.md`, `docs/00-project-overview/{progress,roadmap}.md`.

## Architecture

```text
React Frontend (Login / Register / ProtectedRoute)
        │  Authorization: Bearer <token>, attached by apiClient's interceptor
        ▼
customer-service (:8081) ──issues & validates JWTs──┐
        │  shared JWT_SECRET (HS256)                │
        ▼                                           ▼
account-service (:8082) ───validates only──► transaction-service (:8083) ───validates only
        │  forwards caller's token                        ▲
        └──── recordTransaction() ─────────────────────────┘
                                          transaction-service GETs call back to
                                          account-service's GET /accounts/{id}
                                          (forwarding the caller's token) to
                                          verify ownership — fails closed
```

Full detail: [docs/security/jwt.md](../security/jwt.md) (token format, shared-secret rationale) and [docs/security/authorization.md](../security/authorization.md) (per-service ownership mechanics).

## Migration numbering deviation

The task instructions for this phase specified `V4__add_authentication.sql`. The actual state of `customer-service/src/main/resources/db/migration/` had only `V1__*.sql` — there was no `V2`/`V3` to skip. Rather than create a migration numbered `V4` with a gap before it (which Flyway would accept, but which would misrepresent the schema's actual history to anyone reading the migration directory later), the new migration was named `V2__add_authentication.sql`, immediately following the real `V1`. This deviation is stated here explicitly per this project's honesty-about-verification rule, rather than silently doing something different from the instructions without a record of it.

## Tests performed

**Backend (via `docker run maven:3.9-eclipse-temurin-21 mvn verify` — no local JDK/Maven available):**

| Service | Result |
|---|---|
| customer-service | BUILD SUCCESS, 35 tests, 0 failures |
| account-service | BUILD SUCCESS, 19 tests, 0 failures |
| transaction-service | BUILD SUCCESS, 18 tests, 0 failures |

New tests added this phase: `JwtServiceTest` (generate/parse round-trip, malformed token, token signed with a different key, expired token, secret-too-short constructor validation), `AuthServiceImplTest` (register success/duplicate-email/no-leaked-password, login valid/unknown-email/wrong-password/disabled-account), and `*_throwsAccessDeniedException_when...`/`*_returns403_when...` ownership-denial tests added to every existing `*ServiceImplTest`/`*ControllerTest` across all three services.

**Frontend:** `tsc -b` (strict type-check) and `vite build` both succeeded with no errors. A temporary local `vite` dev server confirmed `/login`, `/register`, and `/transfer` all serve `200` (SPA shell) before being stopped.

**Docker / integration (manual, `docker compose` still unavailable — see Environment constraints below):** rebuilt all four images against the Phase 3A code; restarted the three backend containers against the *existing* Postgres container/volume (still running from a prior phase) specifically to exercise Flyway applying `V2` on top of already-migrated `V1` data, confirmed in `customer-service`'s startup log (`Current version of schema "public": 1` → `Successfully applied 1 migration ... now at version v2`); then ran a full live smoke test against the real stack with `curl`:

- Registered two real customers ("Alice", "Bob"); response bodies contained no password/hash field.
- Duplicate-email registration → `409`.
- Login with correct credentials → `200` + JWT for both customers.
- Login with wrong password and with an unknown email → both `401`, byte-identical generic body.
- `GET /auth/me` with a valid token → `200`; with no token → `401`.
- `GET /customers/{aliceId}` with Alice's own token → `200`; with Bob's token → `403`; with no token → `401`; with a malformed token → `401`.
- Created an account for Alice with a $500 initial deposit (`POST /accounts`, no `customerId` in the body) → `201`, and the initial-deposit transaction was recorded (verified via `GET /transactions/account/{id}` returning both the initial deposit and a subsequent top-up).
- `GET /accounts/{id}` and `POST /accounts/{id}/deposit` with Alice's token → `200`; with Bob's token → `403` both times (confirming the earlier bug-fix — routing the bearer token into `createAccount`'s initial-deposit call — actually works end-to-end, since that same code path is what let the account-creation smoke-test step succeed).
- `GET /transactions/account/{id}` (transaction-service, cross-service ownership check via callback to account-service) with Alice's token → `200` with both transactions; with Bob's token → `403`; with no token → `401`; with a garbage token → `401`.

## Problems encountered and fixes

**1. Initial deposit's transaction record silently NPE-prone.** While threading the caller's bearer token through `AccountServiceImpl`, `createAccount()`'s call to `transactionClient.recordTransaction(...)` for a positive `initialDeposit` was initially left passing `null` for the token (an oversight from adding the token parameter to `deposit`/`withdraw` but not to `createAccount` at the same time). Fixed by adding a `bearerToken` parameter to `createAccount` itself, threaded from the controller — the same fix path as the other two methods. Caught before it reached a running container, by re-reading the diff rather than only running the test suite (the existing test for this method used `any()` matchers that wouldn't have caught a null being passed through).

**2. `@WebMvcTest` + a custom `Filter` bean + `addFilters = false`, layered incorrectly.** The first pass at every controller test used `@AutoConfigureMockMvc(addFilters = false)` (to keep test setup simple — supply an `Authentication` directly via `SecurityMockMvcRequestPostProcessors.user(...)` without needing a real signed token) plus a `@MockBean` for the JWT filter's dependency (`JwtService`/`JwtValidator`), reasoning that `@WebMvcTest` auto-includes `Filter`-typed beans regardless of that flag. That got past a first context-loading failure (`UnsatisfiedDependencyException` on the filter's own dependency), but every test whose controller method used a plain `Authentication` parameter then failed with an unexplained `500`. Root cause, confirmed by inspecting the actual stack trace and Spring's argument-resolution behavior rather than guessing: **`addFilters = false` also disables the Spring Security filter that populates `HttpServletRequest.getUserPrincipal()`**, which is what a plain `Authentication`-typed controller parameter resolves from — so it silently stayed `null`, and `CurrentUser.id(authentication)` threw a `NullPointerException` calling `.getName()` on it.

Fixed by removing `addFilters = false` and instead `@Import`ing the real `SecurityConfig` (plus the `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler` it depends on, since those are plain `@Component`s a `@WebMvcTest` slice doesn't auto-detect either) into each affected controller test, with only the JWT service/validator's `parseClaims(...)` mocked per test to return a `Claims` object with the desired subject. This surfaced a **second**, independent issue along the way: without `SecurityConfig` explicitly imported, `@WebMvcTest` doesn't auto-detect a custom `@EnableWebSecurity` configuration class either, so Spring Boot was silently falling back to its own auto-configured default security chain (CSRF-protected, form-login-based) — visible as `403`s on every `POST` (missing CSRF token) once the first fix was in place. Both issues share one root cause and one fix: the real security configuration has to be explicitly wired into the test context, not assumed to be picked up implicitly. This is now the pattern used by all four rewritten controller test classes (`CustomerControllerTest`, `AuthControllerTest`, `AccountControllerTest`, and, unaffected but left for symmetry, `TransactionControllerTest`, whose controller never uses a plain `Authentication` parameter and so was never exposed to this bug).

**3. `ObjectMapper` missing the JSR310 module in a hand-rolled test.** `AuthServiceImplTest.register_neverLeaksPasswordOrHash_inResponseJson` serialized a `CustomerResponse` (which has a `LocalDate dateOfBirth`) with a bare `new ObjectMapper()`, which has no `java.time` support registered by default and threw `InvalidDefinitionException`. Fixed with `.registerModule(new JavaTimeModule())` on that one throwaway mapper instance.

## Decisions

Recorded in full in [ADR-002](../architecture/decisions/ADR-002-authentication.md): authentication stays inside `customer-service` rather than a new service; JWT with a shared secret rather than a session store or a per-request validation callback; per-service ownership-check ordering tuned to each ID's enumeration sensitivity; `AccountCreateRequest.customerId` removed outright rather than kept-but-ignored; `POST /api/v1/transactions` authenticated but not ownership-re-checked, with the reasoning for why that's safe given its only real caller.

## Known, accepted gaps (not fixed — by design)

Recorded in full in [docs/security/threat-model.md](../security/threat-model.md): no token revocation, no rate limiting, token kept in `localStorage` (XSS-readable), no MFA, no password reset, no auth event audit log, no CSRF protection (correct for a bearer-token API, not an oversight). None of these are silently assumed solved — see the threat model for why each is acceptable for this phase specifically.

## Environment constraints hit during this work

- No local Java/Maven — all backend builds and tests run inside a `maven:3.9-eclipse-temurin-21` container, same as every prior phase.
- No `docker compose` (v1 or v2) binary available — same as every prior phase; the full stack was rebuilt and wired by hand (`docker build` per image, `docker run` per container against the existing `banksphere-net` network), matching `docker-compose.yml`'s service definitions exactly, including the new `JWT_SECRET`/`JWT_EXPIRATION`/`ACCOUNT_SERVICE_URL` environment variables added to that file this phase.
- No browser available — frontend correctness verified via `tsc -b`/`vite build` and a temporary local dev server's HTTP status checks (not actual rendered/interactive UI), plus the Docker frontend image's own build and a live container smoke test of its routes.

## Next

Phase 3B (money transfer between BankSphere customers) is the natural next step, now that a real identity and ownership model exists to build it on. This phase's task instructions were explicit: do not start it here.
