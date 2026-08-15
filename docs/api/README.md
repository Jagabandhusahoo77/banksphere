# API Endpoints

_Status: covers `customer-service`, `account-service`, `transaction-service`, `beneficiary-service`, `employee-service`, and `kyc-service` — the six services implemented so far. No API Gateway exists yet, so each service is called directly at its own port. Verified against the actual implementation, most recently in the Phase 9D customer-OTP/step-up-authentication work — see [docs/09-engineering-journal/](../09-engineering-journal/) for that phase's narrative, [docs/security/](../security/) for the authentication/authorization design (customer-facing), [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md)/[ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md) for OTP/step-up, and [docs/architecture/employee-platform.md](../architecture/employee-platform.md)/[employee-operations.md](../architecture/employee-operations.md) for the employee channel._

All request/response bodies are JSON. All monetary amounts are backed by `BigDecimal` server-side — never floating point — and are serialized as plain JSON numbers (e.g. `"balance": 100.00`, not a quoted string). Request bodies also accept a JSON number for monetary fields; the examples below use that form to match what the frontend actually sends. All timestamps are ISO-8601 UTC.

**Browser access (CORS):** each service allows cross-origin requests from `banksphere.cors.allowed-origins` (env var `CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`) on `/api/**`, `GET`/`POST`/`PUT`/`DELETE`. As of Phase 9D, **customer-service is the one exception**: it allows credentials (`Access-Control-Allow-Credentials: true`), required for the browser to send/receive the refresh-token cookie (see below) — its `allowedOrigins` is still always an explicit list, never a wildcard, which is what makes this safe. Every other service remains `no credentials`.

**Authentication (Phase 3A; OTP login + refresh tokens as of Phase 9D):** every endpoint below is protected unless explicitly marked **PUBLIC**. Protected endpoints require `Authorization: Bearer <token>`, obtained from `POST /api/v1/auth/login` (password) or `POST /api/v1/auth/otp/verify` (one-time code — see below). A missing/invalid/expired token returns `401`; a valid token for the wrong customer returns `403`. Both login paths also set an HttpOnly `banksphere_refresh_token` cookie, path-scoped to `/api/v1/auth` on customer-service, exchangeable for a fresh access token via `POST /api/v1/auth/token/refresh`. See [docs/security/authentication.md](../security/authentication.md), [docs/security/authorization.md](../security/authorization.md), and [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md) for the full model — this document only lists what each endpoint requires and returns.

## customer-service — `http://localhost:8081`

### `POST /api/v1/auth/register` — PUBLIC

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe@example.com",
    "phone": "+1-555-0100",
    "dateOfBirth": "1990-05-20",
    "address": "123 Main St, Springfield",
    "password": "Password123"
  }'
```

`password` must be at least 8 characters and include at least one letter and one digit. Returns `201 Created` with the customer's profile (**never** a token or password/hash — see [docs/security/threat-model.md#t7--sensitive-data-leaking-into-api-responses](../security/threat-model.md#t7--sensitive-data-leaking-into-api-responses)), `400` on validation failure, or `409` if the email is already registered. Does not log the customer in — call `/login` next.

### `POST /api/v1/auth/login` — PUBLIC

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "jane.doe@example.com", "password": "Password123"}'
```

`200` with `{ accessToken, tokenType: "Bearer", expiresIn, customer: { id, firstName, lastName, email } }`, or `401` with `{"message": "Invalid email or password"}` for *any* failure reason (unknown email, wrong password, or disabled account) — see [docs/security/authentication.md#no-account-enumeration](../security/authentication.md#no-account-enumeration) for why this is intentionally generic.

### `GET /api/v1/auth/me`

Returns `200` with the authenticated customer's own profile (derived entirely from the token — no path/query parameter). `401` with no/invalid token.

### `POST /api/v1/auth/logout`

`204 No Content`. Documented no-op server-side (stateless JWT, nothing to revoke) — see [docs/security/authentication.md#sessions-and-logout](../security/authentication.md#sessions-and-logout). The frontend still calls it for a symmetric API and clears its own stored token regardless of the response.

### `POST /api/v1/customers`

Legacy direct-create path, superseded by `/auth/register` for anything user-facing but kept (now behind authentication, unlike Phase 1) — see [ADR-002](../architecture/decisions/ADR-002-authentication.md). Same request shape as `/auth/register` minus `password`; does not create login credentials.

### `GET /api/v1/customers/{id}`

Ownership is checked before existence: any `id` other than the caller's own returns `403` regardless of whether that ID actually exists. Returns `200` with the customer for the caller's own `id`, `403` for any other `id`, or `404` only in the edge case where the caller's own record can't be found (e.g. deleted after the token was issued — there's no revocation, see [docs/security/jwt.md](../security/jwt.md#what-was-deliberately-not-built)). See [docs/security/authorization.md](../security/authorization.md#customer-service--a-customers-own-profile).

### `PUT /api/v1/customers/{id}`

Same ownership rule as `GET`. Full update (all fields required, including `status`: `ACTIVE` | `INACTIVE` | `SUSPENDED`). Returns `200`, `400`, `403`, or `409` (email conflict).

### `GET /api/v1/customers/employee-lookup/{id}` — Phase 9B

Employee-only (requires an employee JWT — see [docs/architecture/employee-platform.md](../architecture/employee-platform.md) — holding `CUSTOMER_VIEW`; a customer token can never satisfy this). No ownership check — an employee may look up any customer by id, which is the entire point (e.g. confirming a name before a cash deposit). Returns a deliberately slim `{ id, firstName, lastName, status }` — no phone/email/address/dateOfBirth, unlike the self-service `GET /{id}` above. `404` if the customer doesn't exist, `403` if the caller lacks `CUSTOMER_VIEW` or is a customer token, `401` if unauthenticated. See [ADR-007](../architecture/decisions/ADR-007-branch-cash-deposit.md).

### `POST /api/v1/auth/otp/request` — PUBLIC — Phase 9D

```bash
curl -X POST http://localhost:8081/api/v1/auth/otp/request \
  -H "Content-Type: application/json" \
  -d '{"identifier": "jane.doe@example.com"}'
```

`identifier` is an email or phone number. Always `200` with `{ "message": "...", "challengeId": "<uuid>" }` — the message is generic and identical whether or not `identifier` matches a real, enabled customer (see [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md) — never reveals registration status). `429` if the resend cooldown (default 30s) or the per-IP rate limit is exceeded. The OTP itself is never in this response — retrieve it via a real delivery channel (production) or `GET /api/v1/auth/dev/otp-inbox` (local dev only, see below).

### `POST /api/v1/auth/otp/verify` — PUBLIC — Phase 9D

```bash
curl -X POST http://localhost:8081/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"challengeId": "<uuid>", "otp": "123456"}'
```

`200` with the same `AuthResponse` shape as `/auth/login`, plus an HttpOnly `banksphere_refresh_token` cookie (`Set-Cookie`, path `/api/v1/auth`). `400` with `{"message": "Invalid or expired OTP"}` for *any* failure reason (wrong code, expired, already used, locked, or an identifier that never matched a real customer) — deliberately as generic as `/auth/login`'s `401`. `429` if the per-IP verify rate limit is exceeded.

### `POST /api/v1/auth/token/refresh` — requires the refresh cookie — Phase 9D

No request body — reads `banksphere_refresh_token` from the cookie. `200` with a fresh `AuthResponse` and a **new** rotated refresh cookie (the presented one is invalidated — see [ADR-009, Decision 11](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-11--refresh-token-strategy-opaque-httponly-cookie-only-rotation-with-reuse-detection)). `401` if the cookie is missing, expired, revoked, or already-rotated (presenting an already-rotated token is treated as possible theft and revokes every other active token for that customer — the response never distinguishes this case from any other invalid-token reason).

### `POST /api/v1/auth/step-up/request` — Phase 9D

```bash
curl -X POST http://localhost:8081/api/v1/auth/step-up/request \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{
    "purpose": "STEP_UP_TRANSFER",
    "transferContext": {
      "sourceAccountId": "<uuid>", "destinationAccountNumber": "617242043877",
      "destinationIfsc": "BANK0000001", "amount": 100000.00, "currency": "INR"
    }
  }'
```

`customerId` always comes from the token, never the body. `200` with `{ challengeId, expiresAt }`, bound to the exact operation described (see [ADR-009, Decision 13](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-13--operation-binding-otpcontexthasher-not-a-generic-json-diff)). Only `STEP_UP_TRANSFER` is accepted this phase — any other purpose returns `400`.

### `POST /api/v1/auth/step-up/verify` — Phase 9D

`{challengeId, otp}` → `200` with `{ verified: true, challengeId, expiresAt }` on success. `400` for a wrong/expired code (same generic message as OTP login). `403` if the challenge doesn't belong to the caller.

### `POST /api/v1/auth/step-up/confirm` — Phase 9D

Called by account-service (or any future step-up-protected service), never directly by the frontend — forwards the customer's own bearer token. `{challengeId, purpose, transferContext}` (the transferContext being executed, recomputed and compared against what was verified) → `200` with `{confirmed: true, challengeId}` on a match. `403` for a context mismatch (amount/recipient changed since verification — tampering), wrong customer, or wrong purpose. `409` if the challenge isn't `VERIFIED` (not yet verified, already executed/replayed, or expired).

### `GET /api/v1/auth/dev/otp-inbox` — PUBLIC, local development only — Phase 9D

Returns the most recently "delivered" OTPs (`{challengeId, identifier, purpose, otp, createdAt, expiresAt}[]`), for retrieving a real code without a real SMS/email/WhatsApp provider. **This route does not exist at all** unless `banksphere.otp.dev-inbox.enabled=true` (default `true` for local Docker Compose) — a plain `404` when disabled, not a permission check. Must be disabled (or not deployed) in any real environment.

## account-service — `http://localhost:8082`

### `POST /api/v1/accounts`

Open an account, optionally with an initial deposit.

```bash
curl -X POST http://localhost:8082/api/v1/accounts \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountType": "SAVINGS",
    "currency": "USD",
    "initialDeposit": 100.00
  }'
```

**Breaking change from Phase 1:** the request no longer accepts `customerId` — the owning customer is always the authenticated caller (see [ADR-002, Decision 4](../architecture/decisions/ADR-002-authentication.md#decision-4--accountcreaterequestcustomerid-removed-entirely-breaking-api-change)). `accountType` is `SAVINGS` or `CURRENT`. `currency` is a 3-letter ISO 4217 code. `initialDeposit` is optional (defaults to `0`). The request also never accepts `accountNumber` or `ifsc` — both are generated/assigned server-side (Phase 8A) and can't be influenced by the caller. Returns `201` with the account:

```json
{
  "id": "...",
  "customerId": "...",
  "accountNumber": "123456789012",
  "ifsc": "BANK0000001",
  "accountType": "SAVINGS",
  "balance": 100.00,
  "currency": "USD",
  "status": "ACTIVE",
  "createdAt": "...",
  "updatedAt": "..."
}
```

`accountNumber` is a system-generated, unique 12-digit number (`AccountServiceImpl#generateUniqueAccountNumber`, retried on collision). `ifsc` is always the single constant `BANK0000001` — BankSphere is one fictional bank with no branch model yet, so every account shares the same IFSC; it identifies the bank, not the individual account. If `initialDeposit` is greater than zero, a `DEPOSIT` transaction ("Initial deposit") is also recorded in transaction-service, the same as a regular deposit.

### `GET /api/v1/accounts/{id}`

Returns `200` if the caller owns the account, `403` if it exists but belongs to someone else, `404` if it doesn't exist at all — account-service checks existence before ownership (the opposite order from customer-service above), since account IDs are opaque and not enumeration-sensitive. See [docs/security/authorization.md](../security/authorization.md#account-service--an-accounts-owning-customer).

### `GET /api/v1/accounts/customer/{customerId}`

Returns `200` with a (possibly empty) array of accounts if `customerId` matches the caller, `403` otherwise (checked before querying).

### `GET /api/v1/accounts/{id}/balance`

Same ownership rule as `GET /api/v1/accounts/{id}`. Returns `200` with `{ accountId, accountNumber, balance, currency, asOf }`.

### `POST /api/v1/accounts/{id}/deposit`

```bash
curl -X POST http://localhost:8082/api/v1/accounts/<account-id>/deposit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "description": "top-up"}'
```

`amount` must be `>= 0.01`. Returns `200` with the updated account, `400` on invalid amount, `403` if the account isn't the caller's, `404` if not found, `422` if the account is not `ACTIVE`. On success, also synchronously calls transaction-service (forwarding the caller's own token) to record a `DEPOSIT` transaction — best-effort, bounded by a 3s connect / 5s read timeout; see [ADR-001](../architecture/decisions/ADR-001-account-transaction-consistency.md) for exactly what this does and doesn't guarantee.

### `POST /api/v1/accounts/{id}/withdraw`

Same shape and ownership rule as deposit. Additionally returns `422` if the withdrawal amount exceeds the current balance.

### Employee-only endpoints — Phase 9B

Reachable only by an employee JWT (a customer token authenticates as a customer principal here, which never carries these authorities — see [ADR-007](../architecture/decisions/ADR-007-branch-cash-deposit.md)). Not called directly by the employee portal — employee-service's own `/api/v1/operations/**` endpoints call these server-to-server, forwarding the acting employee's own token. See [docs/architecture/employee-operations.md](../architecture/employee-operations.md).

**`GET /api/v1/accounts/employee-lookup?accountNumber=…`** — requires `ACCOUNT_VIEW`. Full account detail (including `customerId`/`balance`) by account number — deliberately more revealing than `resolve-recipient` above, since the caller is a permissioned employee performing a real operation, not a peer customer previewing a transfer. `404` if no account matches.

**`GET /api/v1/accounts/employee-lookup/customer/{customerId}`** — requires `ACCOUNT_VIEW`. Every account for a given customer, no ownership check.

**`POST /api/v1/accounts/{id}/employee-deposit`** — requires `CASH_DEPOSIT`. Same body shape as the customer deposit endpoint (`{ amount, description }`), same underlying `credit()`/`requireActive()` logic — reached through a different, employee-authorized entry point, not a separate implementation. Additionally checks branch scope: a `TELLER`'s own branch IFSC must match the account's `ifsc`, or the request fails with `403` (`BranchScopeViolationException`); `BRANCH_MANAGER`/`ADMIN` are exempt from this check (see ADR-007, Decision 6). Returns `200` with `{ account: AccountResponse, transactionReference }` — `transactionReference` is the real `TXN-...` string if ledger recording succeeded, or `null` if it didn't (best-effort, same as every other deposit — the balance change itself is never rolled back for this). Status codes: `400` invalid amount, `401` unauthenticated, `403` missing `CASH_DEPOSIT` or branch-scope violation, `404` account not found, `409` optimistic-locking conflict, `422` account not `ACTIVE`.

### `POST /api/v1/accounts/resolve-recipient` — Phase 8B

Verifies a recipient exists and is transferable *before* the caller commits to a transfer — matches real bank "verify payee" UX. Deliberately the one account-service endpoint that doesn't check the caller's own ownership of anything, since it looks up *someone else's* account by business identifiers and returns only minimal, non-sensitive data about it. See [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md) for the full design and why this isn't an account-enumeration hole.

```bash
curl -X POST http://localhost:8082/api/v1/accounts/resolve-recipient \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "617242043877",
    "ifsc": "BANK0000001"
  }'
```

Returns `200` with:

```json
{
  "accountNumber": "617242043877",
  "ifsc": "BANK0000001",
  "bankName": "BankSphere"
}
```

No `accountId`, `customerId`, or `balance` — the response structurally cannot leak more than "this exact account number + IFSC pair is a valid, active BankSphere account." Status codes: `400` — malformed account number (not exactly 12 digits) or IFSC (not the `AAAA0AAAAAA` format). `401` — no/invalid token. `404` — no BankSphere account has that account number (`RecipientNotFoundException`). `422` — the IFSC isn't `BANK0000001` (`UnsupportedIfscException` — BankSphere doesn't support other banks/branches yet) or the account exists but isn't `ACTIVE`.

### `POST /api/v1/accounts/transfer` — Phase 7A, recipient identification redesigned in Phase 8B, step-up + idempotency added in Phase 9D

Atomic internal account-to-account transfer. Both the debit and the credit happen inside one database transaction in account-service — either both apply or neither does; see [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md) for the full atomicity/locking/ledger design. The destination is identified by **account number + IFSC**, not an internal id — see [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md) for why the internal account id is never part of this API in either direction.

```bash
curl -X POST http://localhost:8082/api/v1/accounts/transfer \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "<uuid>",
    "destinationAccountNumber": "617242043877",
    "destinationIfsc": "BANK0000001",
    "amount": 5000.00,
    "description": "rent",
    "stepUpChallengeId": null,
    "idempotencyKey": "a-client-generated-opaque-string"
  }'
```

The caller must own `sourceAccountId`; there is no `customerId` field anywhere in the request — same rule as account creation. `destinationAccountNumber`/`destinationIfsc` are resolved to the destination account server-side (via the same lookup `resolve-recipient` uses) and may belong to any customer. **Phase 9D:** `stepUpChallengeId` and `idempotencyKey` are both optional. If the amount is at/above the configured step-up threshold (default ₹50,000) and no `stepUpChallengeId` is supplied, the request is rejected `403` — obtain one via `/auth/step-up/{request,verify}` first (see above) and resubmit the identical request with it included; a wrong/mismatched/expired/already-used challenge is also `403`. `idempotencyKey`, if supplied, makes a retried request with the same key return the original cached result instead of executing a second transfer — see [ADR-009, Decision 15](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-15--idempotency-belongs-to-account-service-not-the-otpauth-layer). Returns `200` with:

```json
{
  "transferId": "...",
  "sourceAccountId": "...",
  "destinationAccountNumber": "617242043877",
  "destinationIfsc": "BANK0000001",
  "amount": 5000.00,
  "currency": "INR",
  "status": "COMPLETED",
  "createdAt": "..."
}
```

`transferId` is an account-service-local correlation id for this response only — it is **not** a transaction-service ledger reference. A transfer produces two independent `TRANSFER`-type ledger rows in transaction-service (one on the source account, one on the destination), each with its own separately generated `TXN-...` reference; the two legs are not currently linked by a shared id in the ledger itself (documented limitation, see ADR-004). The default per-leg description (when the caller doesn't supply one) reads `"Transfer to/from account <accountNumber>"` — a human-readable account number, not the internal id (Phase 8B correction).

Status codes: `400` — malformed request, invalid/zero/negative/null amount, malformed `destinationAccountNumber`/`destinationIfsc`, or the resolved destination equals the source account. `401` — no/invalid token. `403` — `sourceAccountId` isn't owned by the caller, step-up is required but no `stepUpChallengeId` was supplied, or the supplied challenge is invalid/expired/already-used/context-mismatched (Phase 9D). `404` — `sourceAccountId` doesn't exist, or no account exists for `destinationAccountNumber` (`RecipientNotFoundException`). `422` — `destinationIfsc` isn't `BANK0000001` (`UnsupportedIfscException`), either account isn't `ACTIVE`, the currencies differ (no FX capability exists), or the source has insufficient balance. `409` — a genuine concurrent-modification conflict on either account (optimistic-locking failure), or (Phase 9D) a concurrent/in-progress request already using the same `idempotencyKey`.

### Conflict responses (`409`)

All three original services return `409 Conflict` if a database unique constraint is violated — most likely a duplicate email on customer creation losing a race to a concurrent request, or (astronomically unlikely) an account-number or transaction-reference collision. `POST /api/v1/accounts/transfer` additionally returns `409` if a genuinely concurrent modification loses the optimistic-locking race on either account (`Account.version`) — see [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md) for how this was proven against a real database, not just asserted.

## transaction-service — `http://localhost:8083`

### `POST /api/v1/transactions`

Called by account-service after a completed deposit/withdrawal, and after account creation with a positive `initialDeposit`, to append a ledger entry — now requires a valid token (closing the Phase 1 gap where this endpoint had no access control at all), but does **not** re-verify that the caller owns the target account; see [ADR-002, Decision 5](../architecture/decisions/ADR-002-authentication.md#decision-5--post-apiv1transactions-requires-authentication-but-does-not-re-verify-account-ownership) for why that's an accepted, explained trade-off rather than an oversight.

### `GET /api/v1/transactions/{id}`

Returns `200` if the caller owns the transaction's account (verified via a callback to account-service, forwarding the caller's own token), `403` if they don't, `404` if the transaction doesn't exist. See [docs/security/authorization.md](../security/authorization.md#transaction-service--an-accounts-transaction-history-via-account-service) for the fail-closed mechanics of that callback.

### `GET /api/v1/transactions/account/{accountId}?page=0&size=20`

Same ownership rule as above. Paginated transaction history for an account, newest first (`sort=createdAt,desc` by default). Pass `?sort=...` to override.

```json
{
  "content": [ { "id": "...", "transactionReference": "TXN-...", "transactionType": "DEPOSIT", "amount": 50.0, "currency": "USD", "status": "COMPLETED", "description": "top-up", "createdAt": "2026-08-11T13:34:44.852525Z" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

## beneficiary-service — `http://localhost:8084`

Manages beneficiaries a customer can use for a future transfer — see [ADR-003](../architecture/decisions/ADR-003-beneficiary-service.md) for why this is a standalone service and what it deliberately does not do yet (no transfers, no Kafka, no payment switch). Every endpoint requires authentication; the owning customer is always the JWT subject, never a client-supplied value — there is no `customerId` field anywhere in this API.

### `POST /api/v1/beneficiaries`

```bash
curl -X POST http://localhost:8084/api/v1/beneficiaries \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "beneficiaryName": "John Doe",
    "accountNumber": "123456789012",
    "ifsc": "BANK0001234",
    "bankName": "Example Bank",
    "nickname": "John"
  }'
```

`accountNumber` must be 9–18 digits; `ifsc` must match the standard 11-character Indian IFSC shape (4 letters, then `0`, then 6 alphanumeric — e.g. `BANK0001234`); `beneficiaryName`/`bankName` are required; `nickname` is optional. Returns `201` with the created beneficiary, `400` on validation failure, or `409` if this customer already has an **active** beneficiary with the same account number + IFSC (see [database/README.md](../database/README.md) for how that's enforced at the database level, not just in application code).

### `GET /api/v1/beneficiaries`

Returns `200` with this customer's own **ACTIVE** beneficiaries only — the ones usable for a future transfer. A deactivated beneficiary won't appear here even though its row still exists (see `DELETE` below).

### `GET /api/v1/beneficiaries/{id}`

Returns `200` with the beneficiary (regardless of status — `ACTIVE` or `INACTIVE`) if owned by the caller, `403` if it exists but belongs to someone else, `404` if it doesn't exist at all — ownership checked after existence, same reasoning as account-service's `GET /accounts/{id}` (beneficiary ids are opaque UUIDs, not enumeration-sensitive like an email).

### `PUT /api/v1/beneficiaries/{id}`

```bash
curl -X PUT http://localhost:8084/api/v1/beneficiaries/<id> \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"beneficiaryName": "Johnny Doe", "bankName": "Example Bank", "nickname": "Johnny"}'
```

Updates display fields only — `beneficiaryName`, `bankName`, `nickname`. **`accountNumber` and `ifsc` are not editable**; changing which real-world account a beneficiary points to is deliberately modeled as delete-and-re-add, not an in-place edit (see [ADR-003](../architecture/decisions/ADR-003-beneficiary-service.md)). Same ownership rule as `GET`. Returns `200`, `400`, `403`, or `404`.

### `DELETE /api/v1/beneficiaries/{id}`

Soft-deletes: sets `status` to `INACTIVE` rather than removing the row. Returns `204`. Same ownership rule as `GET`. The account number/IFSC combination becomes available again for a new (active) beneficiary once deactivated — see the database doc for the partial-unique-index mechanics.

## employee-service — `http://localhost:8085`

_Employee identity, RBAC, and (Phase 9B) the first real employee-initiated banking operation — see [docs/architecture/employee-platform.md](../architecture/employee-platform.md), [docs/architecture/employee-operations.md](../architecture/employee-operations.md), [ADR-006](../architecture/decisions/ADR-006-employee-identity-and-rbac.md), and [ADR-007](../architecture/decisions/ADR-007-branch-cash-deposit.md). Tokens issued here (`EMPLOYEE_JWT_SECRET`) are cryptographically distinct from customer-service's — never interchangeable in either direction._

### `POST /api/v1/employees/auth/login` — PUBLIC

```bash
curl -X POST http://localhost:8085/api/v1/employees/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "jane.teller", "password": "..."}'
```

Wrong password, unknown username, an `INACTIVE` employee, and a `LOCKED` employee all fail identically (`401`, "Invalid username or password") — no account-enumeration signal, same defense customer-service's login uses. Returns `200` with `{ accessToken, tokenType, expiresIn, employee, roles, permissions, branch }` on success.

### `GET /api/v1/employees/me`

Any authenticated employee may view their own profile. Returns the full profile (roles, permissions, branch, status) — structurally no `passwordHash` field anywhere in the response type.

### `POST /api/v1/employees`, `GET /api/v1/employees`, `GET /api/v1/employees/{id}`, `PUT /api/v1/employees/{id}/status`

All require `EMPLOYEE_MANAGE` (held only by `ADMIN` in the current role mapping). No public employee registration exists — see ADR-006, Decision 6, for how the first admin is bootstrapped.

### `GET /api/v1/operations/customer-search?accountNumber=…` or `?customerId=…` — Phase 9B

Requires `ACCOUNT_VIEW` and `CUSTOMER_VIEW`. Exactly one of the two query parameters must be supplied (`400` otherwise). Internally resolves the customer + every one of their accounts via account-service and customer-service, forwarding the caller's own token to each. Returns `200` with `{ customerId, customerName, accounts: [{ id, accountNumber, accountType, balance, currency, status }] }`.

### `POST /api/v1/operations/cash-deposits` — Phase 9B

```bash
curl -X POST http://localhost:8085/api/v1/operations/cash-deposits \
  -H "Authorization: Bearer <employee-token>" \
  -H "Content-Type: application/json" \
  -d '{"accountId": "<uuid>", "amount": 10000.00, "description": "customer requested faster processing"}'
```

Requires `CASH_DEPOSIT`. `accountId`, `amount`, `description` only — no `employeeId`/`branchId`/`customerId` field exists on this request type; the acting employee's identity/branch always come from their own verified JWT, never the body (see ADR-007). Returns `201` with:

```json
{
  "operationReference": "CD-9462451289",
  "accountId": "...",
  "accountNumber": "617242043877",
  "newBalance": 30000.00,
  "currency": "INR",
  "transactionReference": "TXN-789F8B411FF547808848",
  "status": "COMPLETED",
  "performedBy": "EMP000010",
  "branchCode": "HQ001"
}
```

`operationReference` (`CD-...`) is this service's own reference for the operation — it is **not** transaction-service's `transactionReference` (`TXN-...`), which is carried separately and is `null` if ledger recording failed (best-effort — the deposit itself is never rolled back for that). Status codes: `400` invalid amount/malformed request, `401` unauthenticated, `403` missing `CASH_DEPOSIT` or a branch-scope violation, `404` account not found, `409` a concurrent-modification conflict on the account, `422` account not `ACTIVE`.

### `GET /api/v1/operations/cash-deposits/history` — Phase 9B

Requires `CASH_DEPOSIT`. Returns the caller's own branch's most recent cash deposits (newest first, capped at 20) as a plain array — no pagination, deliberately kept simple. Customer names are resolved live from customer-service at read time, never stored — a per-row lookup failure renders as `null`, not a fabricated name.

### `GET /api/v1/employee/customers/{customerId}/360` — Phase 9C

Requires at least one of `CUSTOMER_VIEW`/`ACCOUNT_VIEW`/`TRANSACTION_VIEW`/`KYC_VIEW` (a floor — see below). Aggregates customer-service, account-service, transaction-service, beneficiary-service, and kyc-service live, forwarding the caller's own token to each; nothing is persisted in employee-service. Returns `200` with:

```json
{
  "customerId": "...",
  "customer": { "available": true, "unavailableReason": null, "data": { "id": "...", "firstName": "...", "lastName": "...", "email": "...", "phone": "...", "status": "ACTIVE", "createdAt": "..." } },
  "accounts": { "available": true, "unavailableReason": null, "data": [ { "id": "...", "accountNumber": "...", "accountType": "SAVINGS", "balance": 500.00, "currency": "INR", "status": "ACTIVE" } ] },
  "transactions": { "available": false, "unavailableReason": "Requires TRANSACTION_VIEW", "data": null },
  "beneficiaries": { "available": true, "unavailableReason": null, "data": [] },
  "kyc": { "available": true, "unavailableReason": null, "data": { "id": "...", "status": "APPROVED", "submittedAt": "...", "reviewedAt": "...", "reviewReason": null, "missingDocumentTypes": [], "documents": [] } },
  "unavailableCapabilities": ["LOANS", "CARDS", "FOREX", "SERVICE_REQUESTS"]
}
```

Section-level graceful degradation, not whole-endpoint `403`: each of the five sections is independently `{available, unavailableReason, data}` depending on the caller's actual permission set — `available: false` means the caller lacks that section's permission (never fabricated, never silently omitted); `available: true` with `data: null`/`[]` means the caller can see it and there's genuinely nothing there yet (e.g. never started KYC, no beneficiaries). `transactions` additionally requires `ACCOUNT_VIEW` (to know which accounts to pull transactions for). `unavailableCapabilities` is a separate, static list of domains that don't exist yet for *any* caller (loans/cards/forex/service requests) — see [ADR-008](../architecture/decisions/ADR-008-kyc-domain-and-document-storage.md).

## customer-service — new employee-facing endpoint (Phase 9C)

### `GET /api/v1/customers/employee-lookup/{id}/profile`

Requires `CUSTOMER_VIEW` (employee token). Additive to the existing Phase 9B `employee-lookup/{id}` (unchanged, still returns the slim `{id, firstName, lastName, status}` shape for the cash-deposit confirmation UI) — this one returns the fuller profile (`id, firstName, lastName, email, phone, status, createdAt`) Customer 360 needs. Still structurally excludes any password/credential field.

## transaction-service — new employee-facing endpoint (Phase 9C)

### `GET /api/v1/transactions/employee/account/{accountId}`

Requires `TRANSACTION_VIEW` (employee token). Unlike the customer-facing `GET /api/v1/transactions/account/{accountId}`, does **not** re-verify account ownership via account-service — the `@PreAuthorize` check is the authorization boundary here, since an employee token was never issued for a specific customer/account. Returns the same paginated `PageResponse<TransactionResponse>` shape as the customer-facing endpoint.

## beneficiary-service — new employee-facing endpoint (Phase 9C)

### `GET /api/v1/beneficiaries/employee/customer/{customerId}`

Requires `CUSTOMER_VIEW` (employee token) — reused rather than introducing a new `BENEFICIARY_VIEW` permission (see ADR-008, Decision 7). This service's first-ever acceptance of an employee-signed token. Returns the customer's `ACTIVE` beneficiaries, same shape as the customer-facing `GET /api/v1/beneficiaries`.

## kyc-service — `http://localhost:8086` (Phase 9C)

_KYC applications, documents, and the review workflow — see [docs/architecture/customer-360-and-kyc.md](../architecture/customer-360-and-kyc.md) and [ADR-008](../architecture/decisions/ADR-008-kyc-domain-and-document-storage.md). Validates both customer-service-issued and employee-service-issued JWTs — the first service with a genuinely dual-principal API surface (`/api/v1/kyc/applications/**` customer-only, `/api/v1/kyc/employee/**` employee-only)._

### Customer-facing (`/api/v1/kyc/applications`)

| Endpoint | Purpose |
|---|---|
| `POST /` | Create — `{panNumber, occupation, annualIncomeRange}`. `409` if a non-terminal application already exists. |
| `GET /me` | The caller's own most recent application. `404` if never started. |
| `GET /{id}` | A specific application — `403` if not owned by the caller. |
| `POST /{id}/documents` (multipart) | Upload — `file` + `documentType` query param (`PAN`/`IDENTITY_PROOF`/`ADDRESS_PROOF`/`BANK_STATEMENT`). `400` unsupported type/size, `409` duplicate, `422` application not in `DRAFT`/`ADDITIONAL_INFORMATION_REQUIRED`. |
| `POST /{id}/submit` | `DRAFT → SUBMITTED`. `422` if any required document type is missing. |
| `POST /{id}/resubmit` | `ADDITIONAL_INFORMATION_REQUIRED → RESUBMITTED`. Same document-completeness check. |

### Employee-facing (`/api/v1/kyc/employee`)

| Endpoint | Permission | Purpose |
|---|---|---|
| `GET /queue?status=` | `KYC_VIEW` | The review queue, optional single-status filter |
| `GET /applications/{id}` | `KYC_VIEW` | Full detail incl. status history, `missingDocumentTypes` |
| `GET /customer/{customerId}` | `KYC_VIEW` | `204` (not `404`) if never started — used by Customer 360 |
| `POST /applications/{id}/start-review` | `KYC_REVIEW` | `SUBMITTED`/`RESUBMITTED → UNDER_REVIEW` |
| `POST /documents/{id}/verify` | `KYC_REVIEW` | Mark one document `VERIFIED` |
| `POST /documents/{id}/reject` | `KYC_REVIEW` | `{reason}` → mark `REJECTED` |
| `GET /documents/{id}/content` | `KYC_REVIEW` | Streams the document's real bytes/content-type |
| `POST /applications/{id}/request-information` | `KYC_REVIEW` | `{reason}` → `UNDER_REVIEW → ADDITIONAL_INFORMATION_REQUIRED` |
| `POST /applications/{id}/approve` | `KYC_APPROVE` | `UNDER_REVIEW → APPROVED` |
| `POST /applications/{id}/reject` | `KYC_REJECT` | `{reason}` → `UNDER_REVIEW → REJECTED` |

Any transition outside the locked-in state machine (see ADR-008) returns `422`; a concurrent conflicting decision on the same application returns `409`.

## Error format

All six services share the same error shape, produced by each service's `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-08-11T13:34:45.077200875Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Account ... has insufficient balance: available=120.0000, requested=500.00",
  "path": "/api/v1/accounts/.../withdraw",
  "details": []
}
```

`details` is populated with per-field messages on `400` validation failures. `401`/`403` responses use the same shape, produced by `JwtAuthenticationEntryPoint`/`JwtAccessDeniedHandler` (for requests Spring Security itself rejects) or by `GlobalExceptionHandler` (for the domain-specific `*AccessDeniedException`s each service throws for its own ownership checks).

## Health checks

Every service exposes `GET /actuator/health` — PUBLIC, no token required (only `health` and `info` are enabled — see each service's `application.yml`).
