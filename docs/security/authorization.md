# Authorization

_Status: implemented in Phase 3A. See [authentication.md](authentication.md) for how a request gets an identity in the first place, and [jwt.md](jwt.md) for the token itself._

## Principle

**An ID in a URL is never trusted on its own.** Every endpoint that returns or mutates a specific customer's, account's, or transaction's data re-derives "whose data is this?" from the caller's own verified JWT (never from a request parameter, path variable, or body field) and checks it against the resource actually being accessed. This is the core banking security property this phase exists to add: a signed-in customer must never be able to view or touch another customer's data by editing an ID.

## Public vs. authenticated endpoints

| Service | Public (no token) | Everything else |
|---|---|---|
| customer-service | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `/actuator/health`, `/actuator/info` | Requires a valid `Authorization: Bearer <token>` |
| account-service | `/actuator/health`, `/actuator/info` | Requires a valid token — including account creation |
| transaction-service | `/actuator/health`, `/actuator/info` | Requires a valid token — including the internal `POST /api/v1/transactions` |

A request with no token, an expired token, or a token that fails signature verification gets `401 Unauthorized` before it reaches any controller logic — see [jwt.md](jwt.md) for exactly how that's decided.

## 401 vs. 403 — the distinction this phase enforces everywhere

- **`401 Unauthorized`** — *who are you?* The request has no usable proof of identity: missing header, malformed token, expired token, or a signature that doesn't verify. Handled uniformly by `JwtAuthenticationEntryPoint` in each service before any controller runs.
- **`403 Forbidden`** — *I know who you are, and the answer is no.* The token is perfectly valid, but the authenticated customer doesn't own the resource they're asking for. Handled by each service's own ownership check, which throws a domain-specific exception (`CustomerAccessDeniedException`, `AccountAccessDeniedException`, `TransactionAccessDeniedException`) mapped to `403` by `GlobalExceptionHandler`.

Verified live end-to-end in this phase's Docker smoke test (two real registered customers, real JWTs): a request with no `Authorization` header returns `401`; the identical request with a valid token belonging to the *wrong* customer returns `403`.

## Ownership checks, service by service

### customer-service — a customer's own profile

`CustomerServiceImpl.getCustomer(id, requestingCustomerId)` / `updateCustomer(...)`:

```java
private void requireOwnership(UUID customerId, UUID requestingCustomerId) {
    if (!customerId.equals(requestingCustomerId)) {
        throw new CustomerAccessDeniedException(customerId);
    }
}
```

**Ownership is checked *before* the repository lookup.** Requesting another customer's profile ID always returns `403`, whether or not that ID actually exists — the response never distinguishes "exists but isn't yours" from "doesn't exist" for someone else's ID, because a customer's email/ID space is exactly the kind of thing an enumeration attack targets (see [authentication.md](authentication.md#no-account-enumeration) for the same principle applied to login).

### account-service — an account's owning customer

`AccountServiceImpl`: `getAccount`, `getBalance`, `deposit`, `withdraw` all check ownership **after** `findAccountOrThrow` — the opposite order from customer-service, and deliberately so: account UUIDs are opaque, randomly-generated identifiers with no relationship to anything guessable (unlike an email address, which is often known or guessable ahead of time), so returning `404` for a genuinely-missing account and `403` for someone else's real account leaks nothing an attacker could act on. `getAccountsByCustomer(customerId, requestingCustomerId)` checks `customerId.equals(requestingCustomerId)` *before* querying, for the same reason as customer-service — that path takes a `customerId` directly, which is a more sensitive value than an opaque account ID.

`AccountCreateRequest` no longer has a `customerId` field at all (a breaking API change from Phase 1 — see [ADR-002](../architecture/decisions/ADR-002-authentication.md)). The owning customer is always `CurrentUser.id(authentication)`, taken from the caller's own verified token — there is no code path where a client can specify whose account is being created.

### transaction-service — an account's transaction history, via account-service

transaction-service doesn't own the concept of "which customer owns this account" — account-service does. So `getTransaction`/`getTransactionsByAccount` call back into account-service:

```java
GET {account-service}/api/v1/accounts/{accountId}
Authorization: Bearer <the caller's own token, forwarded as-is>
```

If that call returns `2xx`, the caller owns the account (account-service already enforced its own ownership check on that same request) and the transaction data is returned. If it returns `403`/`404`/any error, or times out, **transaction-service fails closed** — access is denied, never allowed by default:

```java
try {
    ResponseEntity<Void> response = restClient.get().uri("/api/v1/accounts/{id}", accountId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
            .retrieve().toBodilessEntity();
    return response.getStatusCode().is2xxSuccessful();
} catch (RestClientResponseException ex) {
    return false; // account-service said no (403/404) — respect that
} catch (RestClientException ex) {
    log.warn("Ownership check failed for account {}: {}", accountId, ex.getMessage());
    return false; // timeout, connection refused, 5xx — fail closed, not open
}
```

**Deliberately not re-checked: `POST /api/v1/transactions`.** This endpoint requires a valid JWT (so it's no longer the unauthenticated-by-anyone Phase 1 gap — see the API README's Phase 1 note), but does not re-verify that the caller owns the account the transaction is being recorded against. Two reasons:

1. In practice this endpoint is only ever called by account-service itself, synchronously, from inside a deposit/withdraw/create-account request that account-service has *already* ownership-checked against the same caller.
2. Re-checking here would mean transaction-service calling back into account-service *while account-service's own request to transaction-service is still in flight* — a synchronous call-back-into-the-caller loop that adds latency and a cyclic-dependency risk for a check that's already been done one hop up. See [ADR-002](../architecture/decisions/ADR-002-authentication.md) for the full trade-off discussion.

## What the frontend does with this

`ProtectedRoute` redirects an unauthenticated visitor to `/login` — this is a UX convenience, not a security control. The actual enforcement above is what stops a signed-in customer from reading another customer's data even if they bypass the frontend entirely (a raw `curl` with someone else's account ID and their own valid token, for example) — which is exactly how this phase's smoke test verified it.
