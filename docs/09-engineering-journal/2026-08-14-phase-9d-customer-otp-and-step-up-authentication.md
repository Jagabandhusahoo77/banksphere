# 2026-08-14 — Phase 9D: Customer OTP Authentication + Step-Up Authentication

## Objective

Build production-style customer OTP authentication and step-up authentication for high-risk operations, ahead of building any operation that will need it (cash withdrawal, payments, loans, cards). Two explicitly distinct flows: customer login OTP, and step-up OTP for an already-authenticated customer performing a sensitive operation. Full detail: [ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md) and [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md) — this entry is the narrative of what was actually done, in what order, and what was found along the way.

## Inspection before writing code

Read customer-service's existing `AuthController`/`AuthService`/`JwtService`/`SecurityConfig`/`CurrentUser`, the frontend's `AuthContext`/`Login.tsx`/`authService.ts`/`tokenStorage.ts`, account-service's `TransferRequest`/`AccountServiceImpl.transfer()`, and `docker-compose.yml` before writing anything. Confirmed customer-service already fully owns authentication (registration, credentials, JWT issuance) — a dedicated identity service would duplicate that ownership, not extend it, directly contradicting the task's own "don't build a second auth implementation" instruction. Confirmed the frontend stores its access token in `localStorage` with no refresh concept and no cross-origin credential handling — both needed changing for a real refresh-token cookie to work.

## OTP domain: customer-service, additive to the existing auth flow

Built the full domain in a new `com.banksphere.customer.otp` package: `OtpChallenge`/`OtpChallengeStatus`/`OtpPurpose` entities, `OtpGenerator` (`SecureRandom`), `OtpContextHasher` (the operation-binding SHA-256 mechanism), `OtpRateLimiter` (in-memory, explicitly documented as not production-ready), `MockOtpDeliveryProvider`/`DevOtpInbox`/`DevOtpInboxController` (the latter gated at the bean level via `@ConditionalOnProperty`, not a runtime check), `OtpAuditLog`, and `OtpServiceImpl` orchestrating all of it. Login OTP (`/otp/request`, `/otp/verify`) is additive — the existing password `/login` is untouched, both now issue a refresh-token cookie for consistency.

One self-caught design smell during this work: `OtpServiceImpl`'s `createChallenge` helper initially smuggled the plaintext OTP out to its two callers via a `ThreadLocal`, so it could be handed to `OtpDeliveryProvider` after persisting only the hash. Recognized this as fragile before it was ever "done" — refactored to a private `GeneratedChallenge(OtpChallenge, String plaintextOtp)` record returned directly, removing the `ThreadLocal` entirely.

## Refresh tokens: opaque, HttpOnly cookie, rotation with reuse detection

Added `RefreshToken`/`RefreshTokenStatus`/`RefreshTokenService`/`RefreshTokenCookies`. Opaque 32-byte random values, SHA-256 hashed at rest (reasoned explicitly against BCrypt: an already-256-bit-entropy value needs no slow salted hash). Cookie: HttpOnly, `SameSite=Lax`, path `/api/v1/auth`, `Secure` conditional on `COOKIE_SECURE`. `SameSite=Lax` was chosen deliberately over `None` — `localhost:5173`/`localhost:8081` are same-site (same registrable domain, different port) for cookie purposes even though cross-origin for CORS, so `Lax` works without requiring HTTPS in local dev, and is itself the CSRF defense for the two cookie-bearing endpoints.

## Step-up authentication: bound to one operation, never a trust window

Added `StepUpController` (customer-service: `/step-up/request`, `/step-up/verify`, `/step-up/confirm`) and, on the account-service side, `StepUpPolicy`/`StepUpVerificationClient`/`RestStepUpVerificationClient`. The task's explicit constraint — never "OTP verified → trusted for N minutes" — is enforced by `OtpContextHasher`: a SHA-256 digest over the transfer's exact canonical fields (source account, destination, amount, currency), stored at request time and recomputed/compared at confirmation time. `AccountServiceImpl.transfer()` was restructured (its own private method, `executeTransfer`, extracted so the public `transfer()` becomes a thin idempotency wrapper) to check `StepUpPolicy.requiresStepUpForTransfer(amount)` after every other validation passes but before any balance mutation — no point asking for an OTP on a transfer that would fail anyway.

`confirmStepUpExecution` is called by account-service, forwarding the caller's own bearer token to customer-service — the same "downstream service re-verifies the caller's real token" pattern this codebase has used since Phase 1, now applied cross-service for a same-principal-type confirmation for the first time.

## Idempotency: account-service's own concern, not the OTP layer's

`TransferIdempotencyService`/`TransferIdempotencyRecord`, with a real `UNIQUE (customer_id, idempotency_key)` database index as the actual exactly-once enforcement — the `findBy...` check that precedes it in application code is not itself race-safe under concurrency; the unique index is the real backstop, surfaced as `IdempotencyConflictException` when `saveAndFlush` throws `DataIntegrityViolationException`. Every method on `TransferIdempotencyService` runs in its own `REQUIRES_NEW` transaction, specifically so its bookkeeping survives the main transfer transaction's potential rollback — a pattern that turned out to matter a second time later in this same phase (see below).

## Existing test call sites needed updating for a widened constructor/DTO

`AccountServiceImpl`'s constructor gained four new fields (`idempotencyService`, `stepUpPolicy`, `stepUpVerificationClient`, `objectMapper`); `TransferRequest` gained two new positional fields (`stepUpChallengeId`, `idempotencyKey`). Every existing `AccountServiceImplTest`/`AccountControllerTest`/integration-test call site constructing `TransferRequest` positionally needed the two new `null` arguments added (bulk-fixed via a targeted `perl` regex, then hand-verified) — all amounts in existing tests are well below the default step-up threshold, so none of them needed a step-up mock at all (an unstubbed `@Mock StepUpPolicy` already returns `false`). All 90 pre-existing account-service tests passed unchanged after the update; 24 new Phase 9D tests were added on top.

## A real security bug found via live Docker verification, not by any unit test

After rebuilding customer-service/account-service as real Docker images and wiring them by hand (no `docker compose` binary in this environment, same constraint as every prior phase), a full `curl`-driven walk through the refresh-token reuse-detection sequence — rotate once, then replay the old token, then replay the token issued by the first rotation — produced an unexpected result: the second replay (which should have been rejected, since reuse detection should have revoked the entire family) succeeded with a valid `200`.

Direct inspection of `refresh_tokens` in the live Postgres database confirmed it: the tokens that `revokeAllForCustomer` should have flipped to `REVOKED` were still `ACTIVE`. Root cause: `RefreshTokenService.rotate()`'s reuse-detection branch mutated those entities and then, two lines later, threw `RefreshTokenInvalidException` — both inside the same `@Transactional` method. Spring's default rollback-on-`RuntimeException` behavior rolled back the entire transaction when that exception propagated, silently undoing the revocation while still correctly rejecting the one request that triggered it. The theft-detection guarantee was completely inert in practice; only the mocked unit tests (which never exercise a real Spring transaction boundary) had been checking this path, and they couldn't have caught it.

Fixed by extracting the revocation into `RefreshTokenRevocationService`, a separate `@Component` bean with its own `@Transactional(propagation = Propagation.REQUIRES_NEW)` method — the identical pattern `TransferIdempotencyService` already used for exactly this reason, now applied in customer-service for the first time. Rebuilt the image, re-ran the exact same `curl` sequence, and confirmed via a second direct database query that the fix holds: the sibling token is now correctly rejected. The corresponding unit test (`rotate_detectsReuseOfAnAlreadyRotatedToken_...`) was rewritten as an interaction test (verifying `rotate()` delegates to the new bean) plus a new `RefreshTokenRevocationServiceTest` covering the actual mutation — the state-level assertion that used to live in the wrong place now lives in the right one.

## Frontend: reusing existing surfaces, no new routes

`Login.tsx` gained a "Password" / "One-time code" tab — the password path is byte-for-byte unchanged. `AuthContext` gained `requestOtp`/`verifyOtp` alongside the existing `login`/`register`/`logout`. `Transfer.tsx` gained a stable per-attempt `idempotencyKey` (regenerated when the customer reaches a fresh Review step, reused across the step-up retry of that same attempt) and opens a new `StepUpOtpModal` on a `403` from the backend — the frontend never decides on its own that step-up is satisfied. A `DevOtpInboxPanel` (rendered only under `import.meta.env.DEV`) is reused on both the Login OTP screen and inside `StepUpOtpModal`. `apiClient.ts` gained a silent-refresh-on-401 interceptor, sharing one in-flight refresh promise across concurrent 401s so a burst of simultaneous expired-token requests triggers exactly one rotation, not several racing each other against the single-use rotation backend.

No new top-level route was added — OTP login is a tab on the existing `/login`, step-up is a modal inside the existing `/transfer` flow, matching the task's own "preserve the existing customer experience, don't redesign the portal" instruction.

## Live verification — HTTP layer, with direct database inspection

Rebuilt customer-service and account-service as Docker images, wired them by hand with a Postgres container (`docker network create` + `docker run`, matching `docker-compose.yml`'s env vars/ports exactly — no `docker compose` binary available). Registered a real customer, logged in via OTP (retrieving the code from the dev inbox), created two real accounts, and drove:

- **FLOW 1 (login)**: request → dev-inbox retrieval → verify → access token + `Set-Cookie` (HttpOnly, `SameSite=Lax`, path `/api/v1/auth`, 14-day `Max-Age`) all confirmed.
- **FLOW 2 (invalid OTP / replay)**: wrong code rejected `400`; replaying an already-consumed challenge rejected `400`, same generic message either way.
- **FLOW 3 (transfer step-up)**: a ₹100,000 transfer without a challenge rejected `403`; step-up requested and verified; the same transfer resubmitted with the verified `challengeId` succeeded exactly once, balances confirmed via a direct account lookup.
- **FLOW 4 (replay)**: resubmitting with the same, now-`EXECUTED` `challengeId` rejected `403` ("already been used"), balance confirmed unchanged.
- **FLOW 5 (tampering)**: resubmitting with a different amount but the same verified `challengeId` rejected `403` ("does not match the requested operation"), balance confirmed unchanged.
- **Idempotency**: the identical transfer request (same `idempotencyKey`) submitted twice returned the identical cached `transferId`/timestamp on the second call; balance moved exactly once.
- **Refresh rotation + reuse detection**: verified both before and after the bug fix above, including direct `refresh_tokens` table inspection.
- **Logout**: revokes the presented refresh token and returns a cookie-clearing `Set-Cookie` (`Max-Age=0`).

All of the above were exercised as real HTTP calls against real running containers with a real Postgres database — not asserted from code reading, and where a `curl` result alone was ambiguous (the reuse-detection bug), resolved by querying the database directly rather than trusting the HTTP status code in isolation.

## What was not verified: real browser E2E

No Puppeteer/Playwright installation exists in this environment this phase (unlike Phase 8A onward, where `puppeteer-core` pointed at the system Chrome binary was available and used for a real-browser walkthrough). `google-chrome`/`chromium` binaries are present, but no browser-automation package is installed, and installing one purely for this verification step — while technically possible — was judged not worth the added time given that the `curl`-against-live-containers work above already exercised every security-critical behavior (including real `Set-Cookie` headers and real database state, which a browser run would not add meaningfully more confidence about), and the Vitest component tests (`Login.test.tsx`, `Transfer.test.tsx`) already exercise the same user interactions (typing, tab-switching, clicking) against a mocked API layer. Documented here honestly rather than claimed: what a real browser run would add beyond both of those is genuine cross-origin cookie behavior enforced by an actual browser's CORS/cookie engine, which neither `curl` nor `jsdom` fully replicates — this specific gap is real and unclosed.

## Docker environment note (unchanged from every prior phase)

No `docker compose` (v1 or v2) binary available in this environment — confirmed again this phase. Verification was done by building the two changed service images individually (`docker build`) and wiring them by hand with `docker network`/`docker run`, replicating `docker-compose.yml`'s env vars/ports/network exactly — the same substitute method every prior phase has used. `docker-compose.yml` and `docker/local/.env.example` were still updated with every new env var this phase introduced, as the source-of-truth reference for an environment where compose is actually available.

## Build and test results

- customer-service: 118 tests passing (32 `OtpServiceImplTest`, 10 `RefreshTokenServiceTest`, 2 `RefreshTokenRevocationServiceTest`, 6 `OtpContextHasherTest`, 4 `OtpRateLimiterTest`, 3 `DevOtpInboxControllerTest`, 13 `AuthControllerTest`, 4 `StepUpControllerTest`, plus all pre-existing tests unchanged).
- account-service: 113 unit/controller tests passing (54 `AccountServiceImplTest` including 11 new Phase 9D scenarios, 45 `AccountControllerTest`, 8 `TransferIdempotencyServiceTest`, 6 `StepUpPolicyTest`) + the two pre-existing real-Postgres integration tests (not run this session — `mvn test` only; `AccountTransferConcurrencyIT`/`AccountTransferRollbackIT` require `mvn verify` and a reachable Postgres, gated by `PostgresAssumptions`).
- Zero regressions: employee-service (93), transaction-service (20), beneficiary-service (30), kyc-service (65) — all re-run this session, all passing. **439 backend tests total across all six services.**
- Customer frontend: `tsc -b && vite build` clean; 43 Vitest tests passing (6 new: 4 in `Login.test.tsx`, 2 new scenarios in `Transfer.test.tsx` including the full step-up flow with idempotency-key-reuse assertions), zero regressions.
- employee-portal: `tsc -b && vite build` clean; 24 tests passing, unchanged — not touched this phase (customer-facing only).

## What was deliberately not built this phase

Cash withdrawal, payments, service requests, loans, cards, forex, international transfers, notifications, a real SMS/Email/WhatsApp delivery provider, a Notification Service, distributed (Redis-backed) rate limiting, authenticator-app TOTP, adaptive/AI-driven step-up policy, and any AWS/Kubernetes infrastructure — all per the task's own explicit scope boundary, all documented as future extension points in ADR-009 rather than silently skipped. `StepUpPolicy.requiresStepUpForWithdrawal()`/`requiresStepUpForBeneficiaryCreation()` exist as real, tested methods but are not wired to any endpoint, since neither endpoint exists yet.
