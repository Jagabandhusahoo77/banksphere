# ADR-002: Authentication & Authorization

**Status:** Accepted for Phase 3A

## Context

Through Phase 1 and Phase 2A/2B, BankSphere had no real authentication. "Login" meant typing in an existing customer's UUID, which the frontend verified existed via `GET /api/v1/customers/{id}` and then stored in `localStorage` — anyone who knew (or guessed, or intercepted) another customer's UUID could fully "sign in" as them, and every backend endpoint accepted any `customerId`/`accountId` in a request with no check that the caller had any relationship to it. This was explicitly acceptable for Phase 1/2 (documented as a known, deliberate gap — see `docs/00-project-overview/scope.md` and the API README's "Known gap" notes) but is exactly the property this phase closes.

Phase 3A's explicit requirements: real registration/login/logout, BCrypt password hashing, JWT-based sessions, protected APIs across all three existing services, protected frontend routes, and — the core requirement — account/customer ownership validation so a customer can never access another customer's data by changing an ID. Money transfer, a separate `auth-service`, Redis-backed sessions, and OAuth/social login were all explicitly out of scope.

## Decision 1 — Authentication lives in customer-service, not a new `auth-service`

An empty `auth-service` directory already exists as a scaffold from the original 14-service roadmap (`docs/00-project-overview/roadmap.md`, row 6, "Not started"). This phase does not populate it.

**Why:** a customer's credentials are a 1:1 extension of their profile — there is one row in `customer_credentials` per row in `customers`, created together at registration. Splitting authentication into a separate service today would mean every login and every token-validating request either calls out to a second new service or duplicates customer data into it, for no benefit at this scale and no requirement (SSO, multiple credential types, external identity providers) that would justify the split. The task instructions for this phase were explicit on this point: "user registration/login/logout via customer-service (NOT a separate auth microservice for this phase)." Revisit if/when a real `auth-service` phase is reached and multiple identity sources or SSO become a requirement.

## Decision 2 — JWT with a secret shared across services, not a session store or a callback-per-request

**Alternatives considered:**
- **Opaque session token + shared session store (Redis).** Rejected: Redis is explicitly out of scope until its own later phase (see `docs/00-project-overview/scope.md`), and this phase's task instructions explicitly forbid introducing it early.
- **Opaque token + validation callback to customer-service on every request.** Rejected: makes every authenticated request to account-service/transaction-service depend on a synchronous network call to customer-service, adding latency and a hard availability coupling (account-service can't authenticate anyone if customer-service is briefly down, even for requests that don't touch customer data at all).
- **JWT with a shared HMAC secret (chosen).** Each service can verify a token's authenticity offline, with no network call and no shared mutable state — only a shared static secret, configured identically via `JWT_SECRET`. Full detail: `docs/security/jwt.md`.

**Trade-off accepted:** no revocation. A JWT is valid until it expires, full stop — there is no way to invalidate one early short of rotating the shared secret (which invalidates *every* outstanding token, not just one). This is recorded in `docs/security/threat-model.md` as an accepted gap, not solved here — a real revocation mechanism (blocklist, short-lived tokens + refresh tokens, or a session store) is deferred to whichever future phase introduces Redis.

## Decision 3 — Ownership checked in the service layer, not only via database foreign keys

Each service (`customer`, `account`, `transaction`) has its own database (see `docs/database/README.md`) — there is no cross-service foreign key that could enforce "this account belongs to this customer" at the schema level even if we wanted one. Ownership is therefore an explicit, testable check in application code: `requireOwnership(resourceOwnerId, requestingCustomerId)`-shaped logic in each service's implementation class, exercised by a dedicated test per service (`*ServiceImplTest`'s `*_throwsAccessDeniedException_when...` tests) and again at the HTTP layer (`*ControllerTest`'s `*_returns403_when...` tests).

**Ordering differs deliberately between services** — documented in full in `docs/security/authorization.md`, summarized here: customer-service checks ownership *before* the database lookup (so "not yours" and "doesn't exist" are indistinguishable for another customer's ID — emails/customer IDs are the more enumeration-sensitive value), while account-service checks *after* loading the account (account UUIDs are opaque and not guessable, so distinguishing 404 from 403 leaks nothing useful). This is a considered choice per-service, not an inconsistency.

## Decision 4 — `AccountCreateRequest.customerId` removed entirely (breaking API change)

Phase 1's `POST /api/v1/accounts` took `customerId` directly in the request body — with no authentication, there was no other way to say whose account it was. Now that every request carries a verified identity, keeping `customerId` in the body would mean the *server* had two conflicting sources of truth for "whose account is this" (the token vs. the field) and would have to choose one — and choosing "trust the field" would silently reopen exactly the vulnerability this phase closes (create an account under someone else's customer ID). The field is removed outright rather than kept-but-ignored, so there's no dead, misleading field in the API contract. The owning customer is always `CurrentUser.id(authentication)`. This is a breaking change from Phase 1's request shape, called out explicitly here and in `docs/api/README.md`.

## Decision 5 — `POST /api/v1/transactions` requires authentication but does not re-verify account ownership

This endpoint is authenticated (closing the Phase 1 gap where it had no access control at all — see the API README's prior "Known gap" note), but unlike the two `GET` endpoints in transaction-service, it does not call back to account-service to re-check that the caller owns the account a transaction is being recorded against.

**Why this is acceptable:** in every actual call path, this endpoint is invoked by account-service itself, synchronously, from inside a request (`deposit`/`withdraw`/`createAccount`) that account-service has *already* ownership-checked against the same caller, using the same forwarded token. Re-checking here would require transaction-service to call back into account-service *while account-service's own outbound call to transaction-service is still in flight* — a synchronous loop back into the caller that adds a network round-trip and a cyclic-dependency risk to re-verify something one hop up the call stack already verified moments earlier. The two `GET` endpoints (`getTransaction`, `getTransactionsByAccount`) *do* perform this callback, because those are reachable directly by the frontend/any authenticated client, not only from inside an already-checked account-service request — the risk profile is different, so the check is applied where it's actually needed. See `docs/security/authorization.md` for the full mechanism.

## Decision 6 — Frontend keeps a derived `customerId` alongside the new `customer` object

`AuthContext`'s new shape exposes both `customer: AuthenticatedCustomer | null` (the actual authenticated identity) and `customerId: string | null` (`customer?.id ?? null`) so that existing data-fetching hooks (`useCustomer(customerId)`, `useAccounts(customerId)`, used across `Dashboard`, `Accounts`, `Transactions`) did not need to change their call sites. This is a pragmatic compatibility choice, not a security-relevant one — `customerId` is never used to make an authorization decision anywhere; the backend always re-derives identity from the token regardless of what the frontend sends.

## Consequences

- Every existing protected endpoint's method signature changed to accept the requesting customer's identity (`Authentication`/a derived `UUID`), and every corresponding test was updated — see the Phase 3A engineering journal entry for the full list and the `@WebMvcTest` security-wiring issue hit and fixed along the way.
- `docker-compose.yml` and `docker/local/.env.example` gained `JWT_SECRET` (all three services) and `JWT_EXPIRATION` (customer-service only) — externalized configuration, never hard-coded, with a clearly-labeled local-development-only default.
- Money transfer (Phase 3B) can now be built on top of a real identity and ownership model instead of the Phase 1/2 trust-anything one — but transfer itself is explicitly not implemented in this phase (a "Transfer (Coming Soon)" nav entry exists; the route renders an honest not-yet-available state).

## Do NOT implement now

Token revocation, refresh tokens, MFA, password reset, rate limiting, and an audit log of auth events are all recorded as known gaps in `docs/security/threat-model.md`, not implemented here. None of Decision 2's rejected alternatives (Redis-backed sessions, a separate `auth-service`) should be introduced ahead of their designated phase.
