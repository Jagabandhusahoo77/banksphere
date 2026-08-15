# Customer OTP Authentication and Step-Up Authentication

Phase 9D. See [ADR-009](decisions/ADR-009-customer-otp-and-step-up-authentication.md) for the full reasoning behind every decision summarized here; this document is the descriptive reference — what exists and how the pieces fit together.

## The two flows

**Login OTP** — an *unauthenticated* customer proves who they are using a one-time code instead of (or alongside — password login is unchanged and still available) a password.

**Step-up authentication** — an *already-authenticated* customer proves intent for one specific sensitive operation (currently: a transfer at or above a configurable threshold) before it executes. Not login. Not a broad "trusted for the next N minutes" window — bound to the exact operation requested, nothing else.

```
Login OTP:
  Customer Portal → enter email/phone → POST /auth/otp/request
                  → OTP screen → POST /auth/otp/verify
                  → access token + refresh-token cookie → Customer Dashboard

Step-up (transfer):
  Customer Portal → build transfer → POST /accounts/transfer (no challenge yet)
                  → 403 "step-up required" → POST /auth/step-up/request (bound to this exact transfer)
                  → StepUpOtpModal → POST /auth/step-up/verify
                  → retry POST /accounts/transfer with stepUpChallengeId
                  → account-service calls POST /auth/step-up/confirm on customer-service
                  → transfer executes exactly once
```

## Where this lives

All of it is in `customer-service`, `com.banksphere.customer.otp` package — see ADR-009 Decision 1 for why this isn't a new service. `account-service` gets one new outbound dependency (confirming a step-up challenge) and one new local concern (transfer idempotency, which is *not* part of the OTP domain — see Decision 15).

## OTP domain model (customer-service)

```
OtpChallenge
  id, identifier, purpose (OtpPurpose), customerId (nullable — null means the
  identifier didn't match a real customer, see enumeration prevention below),
  otpHash, contextHash (null for LOGIN), status (OtpChallengeStatus),
  attemptCount, maxAttempts, expiresAt, consumedAt, requestedIp, createdAt

OtpPurpose:      LOGIN, STEP_UP_TRANSFER, STEP_UP_WITHDRAWAL,
                 STEP_UP_BENEFICIARY, STEP_UP_PROFILE_CHANGE
                 (only LOGIN and STEP_UP_TRANSFER wired to real endpoints this phase)

OtpChallengeStatus:
  PENDING → VERIFIED (step-up only) → EXECUTED (step-up only)
  PENDING → CONSUMED (LOGIN's terminal success)
  PENDING/VERIFIED → EXPIRED | LOCKED

RefreshToken
  id, customerId, tokenHash (SHA-256), status (RefreshTokenStatus),
  replacedByTokenId, expiresAt, revokedAt, createdAt

RefreshTokenStatus: ACTIVE, ROTATED, REVOKED
```

Migration: `customer-service/.../db/migration/V3__add_otp_challenges.sql` (both tables).

## Supporting classes

| Class | Responsibility |
|---|---|
| `OtpGenerator` | `SecureRandom`-backed numeric code generation |
| `OtpContextHasher` | SHA-256 over an ordered canonical field list — the operation-binding mechanism (ADR-009 Decision 13) |
| `OtpRateLimiter` | In-memory sliding-window limiter, per-IP, per-bucket — demo only, not distributed (Decision 6) |
| `OtpDeliveryProvider` / `MockOtpDeliveryProvider` | The delivery seam; only a logging mock exists this phase |
| `DevOtpInbox` / `DevOtpInboxController` | Dev-only retrieval of a delivered code; the controller bean doesn't exist at all unless `banksphere.otp.dev-inbox.enabled=true` |
| `OtpAuditLog` | Structured audit events — see ADR-009 Decision 16 for the full event list and what's never logged |
| `RefreshTokenService` | Issue / rotate (with reuse detection) / revoke |
| `RefreshTokenRevocationService` | The family-wide revocation, in its own `REQUIRES_NEW` transaction — see ADR-009 Decision 11 for why this had to be split out |
| `RefreshTokenCookies` | The one HttpOnly cookie this service ever sets |
| `OtpServiceImpl` | All of the above, orchestrated — the single implementation of `OtpService` |

## API endpoints

All under `/api/v1/auth` on customer-service unless noted.

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /otp/request` | Public | Step 1 of login OTP — `{identifier}` → generic response + `challengeId` |
| `POST /otp/verify` | Public | Step 2 — `{challengeId, otp}` → `AuthResponse` + refresh cookie |
| `POST /token/refresh` | Refresh cookie | Rotates the refresh token, returns a fresh access token |
| `POST /logout` | Access token | Revokes + clears the refresh cookie (in addition to the existing stateless-JWT no-op) |
| `POST /step-up/request` | Access token | `{purpose, transferContext}` → `challengeId` bound to that exact operation |
| `POST /step-up/verify` | Access token | `{challengeId, otp}` → marks the challenge `VERIFIED` |
| `POST /step-up/confirm` | Access token (forwarded by account-service) | Recomputes the context hash and marks `EXECUTED` — see below |
| `GET /dev/otp-inbox` | Public, dev-profile-gated | Local development only — see the table above |

`POST /api/v1/accounts/transfer` (account-service) gained two optional request fields: `stepUpChallengeId`, `idempotencyKey`. No new endpoint — see ADR-009 Decision 14.

## Refresh tokens

Opaque 256-bit random values, SHA-256 hashed at rest, delivered exclusively via an HttpOnly, `SameSite=Lax`, path-scoped (`/api/v1/auth`) cookie (`banksphere_refresh_token`) — never in a JSON body, never in `localStorage`. Rotated on every use; presenting an already-rotated token revokes every active token for that customer (reuse detection). Full reasoning, including a real bug found and fixed via live verification, in ADR-009 Decision 11.

`COOKIE_SECURE` (default `false`) must be `true` in any real HTTPS deployment — a browser silently refuses to send a `Secure` cookie over plain HTTP, which would break refresh entirely in local dev if left on by default.

## Step-up + transfer integration (account-service)

`AccountServiceImpl.transfer()` is unchanged in its core validation/mutation logic (see ADR-004). Phase 9D wraps it with:

1. Optional idempotency check (`TransferIdempotencyService`) — see ADR-009 Decision 15; this is an account-service-local concern, not part of the OTP domain.
2. Every existing validation, unchanged.
3. `StepUpPolicy.requiresStepUpForTransfer(amount)` — if required and no `stepUpChallengeId`, `403 StepUpRequiredException`. If a challenge id is present, `StepUpVerificationClient` confirms it with customer-service (forwarding the caller's own bearer token) *before* any balance mutation. An unreachable customer-service is never treated as "confirmed" — this call must propagate failure.

Configuration: `banksphere.step-up-policy.transfer-threshold` (account-service, default `50000.00`), `banksphere.customer-service.base-url` (account-service, points at customer-service for the confirm call).

## Frontend (`frontend/`)

- `pages/auth/Login.tsx` — a "Password" / "One-time code" tab toggle; password path is byte-for-byte the same flow as before this phase.
- `context/AuthContext.tsx` — gained `requestOtp`/`verifyOtp`, alongside the unchanged `login`/`register`/`logout`.
- `services/authService.ts` — `requestOtp`, `verifyOtp`, `getDevOtpInbox`.
- `services/stepUpService.ts` — new file — `requestTransferStepUp`, `verifyStepUp`.
- `components/common/StepUpOtpModal.tsx` — reusable step-up UI: requests a fresh challenge on open, shows the operation being authorized, verifies the code, hands the verified `challengeId` back to the caller (which is responsible for retrying the actual operation — the modal never calls a banking endpoint itself, the same intent-vs-execution boundary `docs/chatbot/security.md` already establishes for the chatbot).
- `components/common/DevOtpInboxPanel.tsx` — renders only under `import.meta.env.DEV`; the backend route it calls is independently gated (see above) — two independent gates, neither trusted alone.
- `pages/transfer/Transfer.tsx` — generates one `idempotencyKey` per review attempt (stable across the initial submit and its step-up retry); on a step-up-required 403, opens `StepUpOtpModal`; on verification, retries the same transfer with the challenge id.
- `services/apiClient.ts` — `customerApiClient` now sets `withCredentials: true` (needed for the refresh cookie); every client gained a silent-refresh-on-401 interceptor that calls `/token/refresh` at most once per failing request, sharing a single in-flight promise across concurrent 401s.

## Future extension points (not built this phase)

- Real SMS/Email/WhatsApp `OtpDeliveryProvider` implementations, and a Notification Service they might eventually route through.
- `StepUpPolicy.requiresStepUpForWithdrawal()`/`requiresStepUpForBeneficiaryCreation()` wired to real endpoints once those endpoints exist.
- Distributed rate limiting (Redis) once Redis reaches its own designated phase.
- Authenticator-app TOTP as an additional second factor alongside OTP.
- Risk-based/adaptive step-up thresholds informed by the audit stream this phase already produces — see ADR-009 Decision 17 for the explicit constraint that any such system may only ever *tighten* policy, never bypass it.
