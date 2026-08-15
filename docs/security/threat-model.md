# Threat Model — Phase 3A Authentication

_Status: Phase 3A. This is a working threat model for what this phase actually built, not an exhaustive banking-security audit. See [authentication.md](authentication.md), [authorization.md](authorization.md), and [jwt.md](jwt.md) for the mechanisms referenced below._

## In scope for this phase

Phase 3A's stated goal was: real registration/login, password hashing, JWT-based session identity, and — the property this whole phase exists to protect — **a signed-in customer can never read or modify another customer's data by manipulating an ID.** Everything below is evaluated against that goal.

## Threats considered and how they're addressed

### T1 — Horizontal privilege escalation (customer A accesses customer B's data)

**The primary threat this phase defends against.** Addressed by re-deriving the caller's identity from their own verified JWT on every request and checking it against the resource, never trusting an ID from the URL/body — see [authorization.md](authorization.md) for the ownership check in each of the three services. Verified live: two real registered customers, cross-customer requests for profile/account/transaction data all returned `403`.

### T2 — Unauthenticated access to protected data

Addressed by `authorizeHttpRequests().anyRequest().authenticated()` in each service's `SecurityConfig`, with an explicit, short allow-list of public paths (register, login, health/info). Verified live: every protected endpoint returns `401` with no `Authorization` header.

### T3 — Password compromise via the database

Addressed by BCrypt hashing (adaptive cost, per-password salt built into the hash) — a stolen `customer_credentials` table yields no usable plaintext without a per-row brute-force. See [authentication.md](authentication.md#password-storage).

### T4 — Account enumeration via login behavior

Addressed by a single generic error message and a dummy-hash timing-equalization comparison for unknown emails — see [authentication.md](authentication.md#no-account-enumeration). Not addressed: enumeration via the *registration* endpoint's `409` on duplicate email. Registering with an already-used email does confirm that email is registered — this is a common, generally-accepted trade-off (most consumer products behave this way) rather than an oversight, but it's worth stating plainly: registration is not enumeration-safe, only login is.

### T5 — Token forgery

Addressed by HMAC-SHA256 signature verification with a secret that's never sent to the client and must be ≥32 bytes (enforced at startup — see [jwt.md](jwt.md#the-shared-secret)). A token with a tampered payload or a signature produced with a different key fails `parseClaims` and is treated as no-token (`401`), verified by `JwtServiceTest`'s "token signed with different key" test.

### T6 — Cross-service ownership bypass (transaction-service trusting an unverified account ID)

Addressed by transaction-service calling back into account-service (forwarding the caller's own token) before returning transaction data for an account, and **failing closed** on any non-2xx response, timeout, or connection failure — see [authorization.md](authorization.md#transaction-service--an-accounts-transaction-history-via-account-service). This was deliberately verified with a real network failure mode in mind, not just the happy path: `RestAccountOwnershipClient` treats a timeout identically to an explicit `403`.

### T7 — Sensitive data leaking into API responses

Addressed structurally: `CustomerResponse`/`CustomerSummary`/`AuthResponse` are DTOs that simply have no field capable of carrying a password or hash — there's no redaction logic to forget, because the credential entity is never mapped into a response type. Verified by `AuthServiceImplTest.register_neverLeaksPasswordOrHash_inResponseJson`, which serializes the actual response DTO and asserts the JSON contains neither "password" nor "hash" (case-insensitive).

## What this phase does not defend against (accepted, not fixed)

Stated explicitly, per this project's [honesty-about-verification rule](../../CLAUDE.md#honesty-about-verification) — these are known gaps, not things believed to be handled:

- **No token revocation.** A stolen (but not-yet-expired) token remains valid for up to `JWT_EXPIRATION` seconds (default 1 hour) after logout or after the legitimate user notices something is wrong. There is no server-side blocklist. See [jwt.md](jwt.md#what-was-deliberately-not-built).
- **No rate limiting on login or registration.** Nothing in this phase throttles repeated login attempts, so brute-forcing a specific known email's password, or discovering registered emails via `409` on `/register` (see T4), is not slowed down beyond BCrypt's inherent per-attempt cost. Rate limiting typically wants shared state across instances (Redis or similar) — out of scope until that phase.
- **XSS → token theft.** The access token lives in `localStorage`, which any script running on the page can read. This phase does not add a Content-Security-Policy on the frontend or move the token to an httpOnly cookie. See [authentication.md](authentication.md#frontend-token-handling) for the trade-off.
- **No MFA.** Single-factor (password only). Not part of this phase's stated scope.
- **No password reset flow.** There is no "forgot password" endpoint — a customer with a lost password has no self-service recovery path in this phase.
- **No audit log of authentication events.** Login successes/failures are not persisted anywhere beyond `last_login_at` being updated on success; there's no record of failed attempts, IPs, or user agents. A future `audit-service` (already an empty scaffold directory) is a natural home for this.
- **No CSRF protection.** Deliberately disabled (`csrf.disable()` in every `SecurityConfig`) because this is a stateless bearer-token API with no cookie-based session — CSRF is a cookie-auth problem, not a bearer-token one, so disabling it here is correct for this design rather than a gap. This would need revisiting if the token were ever moved to a cookie.
- **No security event alerting/monitoring.** Nothing pages anyone on repeated failed logins, impossible-travel patterns, or similar. Monitoring/observability is its own future phase.

## Threats explicitly out of scope for this phase (by project-level scope discipline)

Not evaluated here because the underlying infrastructure doesn't exist yet in this project (see [scope.md](../00-project-overview/scope.md)): network-layer attacks (no Kubernetes/service-mesh policies exist to test), infrastructure-as-code misconfiguration (no Terraform yet), CI/CD supply-chain risk (no pipeline yet), and anything requiring Redis/Kafka (neither exists yet).
