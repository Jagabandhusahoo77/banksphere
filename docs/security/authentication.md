# Authentication

_Status: implemented in Phase 3A. Covers `customer-service`, `account-service`, and `transaction-service`. See [ADR-002](../architecture/decisions/ADR-002-authentication.md) for why this design was chosen over the alternatives, [authorization.md](authorization.md) for what happens after a request is authenticated, and [jwt.md](jwt.md) for the token format itself._

## Where authentication lives

There is no separate `auth-service` in this phase — `customer-service` owns both the customer domain and authentication, since a customer's credentials are 1:1 with their profile and splitting them into a separate service would add a network hop to every login for no benefit at this scale. `account-service` and `transaction-service` never issue tokens; they only validate ones customer-service issued, using a secret shared by all three (see [jwt.md](jwt.md)).

## Registration

`POST /api/v1/auth/register` (customer-service, public — no token required)

1. Validates the request (`RegisterRequest`): name, email format, phone, date of birth, address, and a password matching `^(?=.*[A-Za-z])(?=.*\d).{8,72}$` (at least 8 characters, at least one letter and one digit — checked again client-side in `Register.tsx` for fast feedback, but the server is the actual gate).
2. Rejects a duplicate email with `409 Conflict` — the same `DuplicateEmailException` used by the pre-existing `POST /api/v1/customers` path.
3. On success, creates a `Customer` row and a separate `CustomerCredentials` row (BCrypt hash only — see below), and returns `201 Created` with the customer's profile. **Never returns a token or auto-logs-in** — the frontend redirects to `/login` with a "registered" banner instead. This keeps registration and authentication as two independently-testable steps and avoids ever putting a fresh, un-verified session in a response body.

## Password storage

- Hashed with `BCryptPasswordEncoder` (Spring Security's default, adaptive cost factor 10) before it ever reaches the database. The plaintext password is never logged, persisted, or included in any response — `RegisterRequest`/`LoginRequest` both override `toString()` to redact it, in case either is ever accidentally logged by a framework or a future debug statement.
- Credentials live in their own table (`customer_credentials`, keyed by `customer_id`), not on the `customers` row — a `CustomerResponse`/`CustomerSummary` DTO is structurally incapable of including a hash, because the entity that carries it is never mapped into those DTOs in the first place.

## Login

`POST /api/v1/auth/login` (customer-service, public)

Takes `{ email, password }`, returns `200` with `{ accessToken, tokenType: "Bearer", expiresIn, customer }` on success, or `401` with the exact message `"Invalid email or password"` on any failure — whether the email doesn't exist, the password is wrong, or the account is disabled. This is deliberate: see "No account enumeration" below.

### No account enumeration

A login failure never reveals *why* it failed:

```java
Optional<Customer> customerOpt = customerRepository.findByEmailIgnoreCase(request.email());
Optional<CustomerCredentials> credentialsOpt = customerOpt.flatMap(c -> credentialsRepository.findById(c.getId()));
String hashToCheck = credentialsOpt.map(CustomerCredentials::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);
boolean credentialsValid = customerOpt.isPresent() && credentialsOpt.isPresent()
        && credentialsOpt.get().isEnabled() && passwordMatches;
if (!credentialsValid) throw new InvalidCredentialsException(); // always the same message
```

Two things matter here beyond the generic exception message:

1. **Timing.** If the email doesn't exist, the code still runs a BCrypt comparison — against a fixed dummy hash (`DUMMY_PASSWORD_HASH`, computed once at class-load time) rather than skipping straight to "invalid." BCrypt is deliberately slow (that's the point of it as a KDF), so skipping the comparison for unknown emails would make "unknown email" responses measurably faster than "wrong password" responses, which is itself an oracle an attacker could use to enumerate registered emails by timing alone. Comparing against a dummy hash keeps the response time approximately the same either way.
2. **Message.** Wrong password, unknown email, and a disabled account all produce the identical `401` body. A disabled account in particular does *not* get a more specific message ("account disabled") — that would tell an attacker the email is valid and just locked, which is still an enumeration leak.

Verified live end-to-end during this phase's Docker smoke test: unknown-email and wrong-password requests against the same account returned byte-identical response bodies and the same `401` status.

## Sessions and logout

Sessions are stateless — there is no server-side session store, and `POST /api/v1/auth/logout` is a documented no-op: it exists so the frontend has a symmetric API to call, but a JWT already issued remains valid (signature-wise) until it expires; there is no revocation list in this phase. Logging out only clears the token from the browser's `localStorage` (see `frontend/src/services/tokenStorage.ts`) — the frontend contract, not the server, is what actually ends the session. This is a known, accepted limitation; see the Threat Model's "What this phase does not defend against."

## Frontend token handling

- The token and the authenticated customer's summary (`{ id, firstName, lastName, email }`) are stored together in `localStorage` under `banksphere.auth` (`tokenStorage.ts`).
- **Trade-off, stated plainly:** `localStorage` is readable by any JavaScript running on the page. If BankSphere ever had an XSS vulnerability elsewhere, that script could read the token. An httpOnly cookie would close this gap but requires the backend to set/manage cookies and handle CSRF instead — a bigger change than this phase's scope. This is a real, accepted risk for a portfolio/demo project, not a claim that this is production-grade session handling.
- `apiClient.ts`'s request interceptor attaches `Authorization: Bearer <token>` to every request automatically; its response interceptor clears the stored session on any `401` (expired/invalid token), and `AuthContext` listens for a `banksphere:auth-cleared` window event to keep React state in sync without prop-drilling a setter into a non-React module.
- `isTokenExpired()` decodes the JWT payload client-side (no signature check — the frontend is never the trust boundary) purely so the UI doesn't show a logged-in state for a token that's already expired, avoiding a guaranteed-to-fail request and a flash of authenticated UI before the redirect to `/login`.

## What the frontend must never be trusted to enforce

The frontend hides UI for things a user can't do and pre-fills what it already knows, but every actual access decision is re-checked server-side. Changing an account ID in the browser's address bar, or hand-crafting a request with a different UUID, is expected and defended against at the API layer — see [authorization.md](authorization.md).
