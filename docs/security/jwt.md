# JWT Implementation

_Status: implemented in Phase 3A. See [authentication.md](authentication.md) for how tokens get issued and [authorization.md](authorization.md) for how they're used to make access decisions._

## Why JWT, and why a shared secret instead of a callback

Three independent services need to agree on "who is this request from?" without a shared session store (Phase 1 explicitly excludes Redis, and Phase 3A doesn't introduce it either). A signed, stateless token lets `account-service` and `transaction-service` verify a caller's identity **without calling back to customer-service on every request** — they just need the same signing secret. The alternative (each service calling customer-service to validate every token) would add a network round-trip and a hard dependency on customer-service's uptime to every authenticated request across the system; a shared-secret HMAC avoids both.

## Library and algorithm

[JJWT](https://github.com/jwtk/jjwt) 0.12.6 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`), HMAC-SHA256 (`HS256`). All three services depend on `jjwt-api` at compile time; only customer-service also needs `jjwt-impl`/`jjwt-jackson` for signing — actually all three carry the same three artifacts for simplicity, since account-service/transaction-service need `jjwt-impl` on the runtime classpath to *parse* tokens even though they never *build* one.

## Who issues, who validates

| Service | Issues tokens? | Validates tokens? | Class |
|---|---|---|---|
| customer-service | Yes | Yes | `JwtService` (both directions) |
| account-service | No | Yes | `JwtValidator` (validate-only) |
| transaction-service | No | Yes | `JwtValidator` (validate-only) |

customer-service is the only service with a `generateToken(...)` method. account-service and transaction-service's `JwtValidator` is deliberately a smaller type than customer-service's `JwtService` — there's no `generateToken` to accidentally call from a service that has no business minting a customer's identity.

## Token shape

Issued by `JwtService.generateToken(UUID customerId, String email)`:

- **Subject (`sub`)**: the customer's UUID, as a string. This is the value every service treats as "who made this request" — `CurrentUser.id(authentication)` parses `authentication.getName()` (which is this subject) back into a `UUID`.
- **Claims**: `email` (the customer's email, informational — never used for an access decision, only `sub` is), `roles: ["ROLE_CUSTOMER"]` (present for future role-based expansion; nothing in this phase currently branches on role — every authenticated customer has exactly one role today).
- **`iat`/`exp`**: standard issued-at/expiry, `exp` = `iat` + `banksphere.jwt.expiration-seconds` (default 3600 = 1 hour, configurable via `JWT_EXPIRATION`).
- **Signature**: `HS256` over the header+payload using the shared secret.

## The shared secret

Configured identically on all three services via `JWT_SECRET` (falls through to a checked-in local-development-only default if unset — see `docker/local/.env.example`). **Must be at least 32 bytes (256 bits)** — each service's constructor validates this at startup and refuses to start otherwise:

```java
byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
if (secretBytes.length < 32) {
    throw new IllegalStateException(
            "JWT_SECRET must be at least 32 bytes (256 bits) for HS256 — configured value is too short");
}
```

This is a fail-fast check, not a runtime one — a misconfigured secret is caught at application startup, not on the first request that happens to need it.

**The three services must be configured with the exact same secret.** If they diverge, tokens issued by customer-service will fail signature verification everywhere else — every authenticated request to account-service/transaction-service would incorrectly return `401`. This is the one piece of shared configuration this phase introduces between services (see `docker-compose.yml`, where all three read the same `JWT_SECRET` environment variable).

## Validation — what happens on every authenticated request

`JwtAuthenticationFilter` (one per service, `OncePerRequestFilter`, registered via `SecurityConfig.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`):

```java
extractToken(request).flatMap(jwtService::parseClaims).ifPresent(claims -> authenticate(claims, request));
filterChain.doFilter(request, response);
```

`parseClaims` never throws — it wraps `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` and turns any `JwtException`/`IllegalArgumentException` (bad signature, expired, malformed, wrong algorithm, etc.) into `Optional.empty()`. The filter *never rejects a request itself* — an absent/invalid token simply leaves `SecurityContextHolder` empty, and Spring Security's own `authorizeHttpRequests` rules (see [authorization.md](authorization.md)) decide whether that's acceptable for the requested path. This keeps "is this path public?" logic in exactly one place (`SecurityConfig`) instead of duplicated inside the filter.

## What was deliberately not built

- **No refresh tokens.** A token simply expires after `JWT_EXPIRATION` seconds and the user has to log in again. Refresh-token rotation adds a second token type, a revocation surface, and (typically) a persistence requirement — out of scope for this phase's session model.
- **No revocation / blocklist.** `POST /api/v1/auth/logout` only clears the frontend's stored token (see [authentication.md](authentication.md#sessions-and-logout)); a token already issued remains cryptographically valid until it naturally expires. Revoking a specific token before its expiry would require a server-side store keyed by token/jti — the exact kind of session state a stateless JWT design is chosen to avoid, and Redis (a natural fit for such a store) is out of scope until its own phase.
- **No key rotation.** One static secret, read from configuration at startup. Rotating it would immediately invalidate every outstanding token (a real, if simple, "log everyone out" lever) and there's no dual-key/grace-period support for a rotation that doesn't do that.

These are recorded as known limitations, not oversights — see the [threat model](threat-model.md) for the full list and [ADR-002](../architecture/decisions/ADR-002-authentication.md) for the reasoning behind the design as a whole.
