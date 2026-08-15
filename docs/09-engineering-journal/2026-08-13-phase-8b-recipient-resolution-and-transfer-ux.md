# 2026-08-13 — Phase 8B: Recipient Resolution and Transfer UX

## Objective

Fix a real UX/architecture problem surfaced in Phase 8's own honest reporting: the Transfer page's "another BankSphere account" path asked the sender to paste the recipient's internal BankSphere Account ID — a UUID — which no real customer has or would ever type. Replace it with account number + IFSC, the identifiers a customer actually has, while keeping the internal UUID entirely server-side and without weakening Phase 7A's atomicity guarantees.

## Root cause of the original mismatch

Traced back to how the two relevant records were modeled independently, correctly, in isolation: `Beneficiary` (beneficiary-service, Phase 6) stores only business identifiers — `accountNumber`/`ifsc`/`bankName` — because that's what a real beneficiary form asks for and beneficiary-service was deliberately built with zero dependency on account-service's internal ids (ADR-003). `Account` (account-service, Phase 7A) resolves transfers by `destinationAccountId` because that was the simplest correct implementation of atomic debit+credit inside one service's own database, and Phase 7A was explicitly scoped as backend-only with no frontend UI to expose that choice's UX cost. Neither decision was wrong on its own — the gap only became visible once Phase 8 wired a real UI on top of both and a real user tried to use a saved beneficiary to send money, discovering there was no bridge between "what a beneficiary stores" and "what a transfer needs." Phase 8's own documentation flagged this explicitly rather than faking a resolution; this phase closes it.

## Design decision: keep the internal UUID server-side, always

Before writing any code, the security question the task raised directly — should `resolve-recipient` return an internal `accountId` for the frontend to later submit? — was resolved by inspecting how ownership/authorization already work in this codebase (`docs/security/authorization.md`): every existing endpoint re-derives "whose data is this" from the JWT and never trusts a client-supplied id for anything sensitive. Returning an `accountId` here and then accepting it back in `/transfer` would create the *first* endpoint pair where a client-supplied internal id, sourced from an untrusted round-trip through the browser, gets used for a money-moving decision — a new pattern this codebase has specifically avoided everywhere else. Instead: `resolveDestinationAccount(accountNumber, ifsc)` is a private helper in `AccountServiceImpl`, called by both `resolveRecipient()` (preview, read-only) and `transfer()` (the actual mutation) — the same server-side lookup, run twice from two different endpoints, so preview and execution can never diverge, and no id ever needs to cross the network in either direction. Full reasoning in [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md).

## Backend changes

- `AccountRepository.findByAccountNumber(String)` — new, the one query this whole phase hinges on.
- `RecipientNotFoundException` (404) and `UnsupportedIfscException` (422) — new, distinct failure modes for "doesn't exist" vs. "we don't support that bank," wired into `GlobalExceptionHandler`.
- `TransferRequest` rewritten: `destinationAccountId: UUID` → `destinationAccountNumber` (`^\d{12}$`) + `destinationIfsc` (`^[A-Z]{4}0[A-Z0-9]{6}$`), both Bean-Validation-checked before the request ever reaches service logic. `TransferResponse` correspondingly drops `destinationAccountId` for the same two fields.
- New `ResolveRecipientRequest`/`ResolveRecipientResponse` DTOs — the response is deliberately minimal (`accountNumber`, `ifsc`, `bankName`; no id, no `customerId`, no balance).
- `AccountServiceImpl.transfer()` rewritten to resolve the destination via `resolveDestinationAccount()` instead of `findById`. IFSC is checked first (cheap, no DB hit) — a non-BankSphere IFSC never even attempts an account-number lookup. The deterministic-ordering/optimistic-locking/`saveAndFlush` logic from Phase 7A is unchanged in shape; it now simply runs on IDs known only *after* resolution, rather than IDs the request carried directly. Default ledger-leg descriptions switched from the destination's internal id to its account number.
- New `resolveRecipient()` service method and `POST /api/v1/accounts/resolve-recipient` controller endpoint — the one account-service endpoint that doesn't check the *caller's* ownership of anything, documented as a deliberate, reasoned exception in the controller's class-level javadoc rather than a silent gap.
- No Flyway migration needed — every column this phase reads (`account_number`, `ifsc`) already existed and was already backfilled in Phase 8A.

## Frontend changes

- `types/account.ts`: `TransferRequest`/`TransferResponse` updated to the new shape; new `ResolveRecipientRequest`/`ResolveRecipientResponse` types.
- `accountService.ts`: added `resolveRecipient()`.
- `Transfer.tsx` recipient step rebuilt around three tabs:
  - **My accounts** — the caller's own other accounts, resolved locally from already-fetched, already-trusted data (no extra request).
  - **Saved beneficiary** — only `status === "ACTIVE"` beneficiaries are listed; selecting one immediately calls `resolve-recipient` with the beneficiary's stored `accountNumber`/`ifsc` to confirm the underlying account is still real and active, rather than trusting the saved snapshot.
  - **Account number** — digit-filtered/uppercase-filtered inputs for account number and IFSC, gated behind an explicit "Verify recipient" button that only enables once both pass their format patterns.
- All three converge on a `resolvedRecipient` state that renders a preview card (bank name, masked account number `****...1234`, IFSC, and — only where a real name exists, i.e. beneficiary/own-account selections, never fabricated for manual entry — a name) and gates the wizard's "Continue" button.
- `handleConfirm()` submits `{ sourceAccountId, destinationAccountNumber: resolvedRecipient.accountNumber, destinationIfsc: resolvedRecipient.ifsc, amount, description }` — the UUID never enters this code path at all.
- Same-account validation moved from comparing UUIDs (no longer available client-side) to comparing the resolved account number against the source account's own account number.

## Tests

**Backend** (`account-service`) — rewritten/extended `AccountServiceImplTest` (34 tests: existing transfer tests updated to mock `findByAccountNumber` instead of `findById` for the destination; new tests for account-number-based resolution, human-readable ledger descriptions, `RecipientNotFoundException`, `UnsupportedIfscException`, and a full `resolveRecipient()` block including the not-active case) and `AccountControllerTest` (28 tests: 400/404/422 status mapping for the new fields, plus a `resolveRecipient()` block that explicitly asserts the response JSON has no `accountId`/`id`/`customerId`/`balance` field — a negative assertion proving the minimal-response design, not just the happy path). Both real-Postgres integration tests (`AccountTransferRollbackIT`, `AccountTransferConcurrencyIT`) updated to generate real 12-digit account numbers via the same `SecureRandom` pattern production uses, since their old `"IT" + hex"` scheme no longer passes the new request-level format validation.

**Frontend** — `Transfer.test.tsx` rewritten (8 tests): source-account requirement, "Verify recipient" disabled until valid format, successful manual resolution and preview, friendly 404 messaging on an unknown account, saved-beneficiary auto-resolution, submitting the resolved account number (never a UUID), double-submit prevention, and honest display of a backend rejection without a fake success path.

## Build and test results

- `mvn clean verify` (Docker `maven:3.9-eclipse-temurin-21`, against the live `banksphere-postgres`): **BUILD SUCCESS**. 64 tests total (28 controller + 34 service + 2 real-Postgres integration), all passing.
- `transaction-service`: re-run to confirm no regressions (untouched this phase) — 12/12 passing, unchanged.
- `npm run build` (`tsc -b && vite build`): clean, zero errors.
- `npm test` (`vitest run`): 37/37 passing.

## Real end-to-end result

Rebuilt and swapped `account-service` and `frontend` Docker images into the live stack. Ran a real-browser (Puppeteer against the actual system Chrome, not `curl`) scenario covering: Customer A transferring to Customer B by typing B's raw account number + IFSC (no UUID shown anywhere in the UI at any point), verifying the recipient preview shows the correctly masked account number and the reviewed IFSC, submitting, and confirming both customers' balances and transaction histories updated correctly on both sides; a second scenario where Customer A instead selects a saved beneficiary for Customer B and the recipient auto-resolves and auto-populates without manual entry; and three explicit security checks — a same-account transfer attempt rejected with `400` before any mutation, a well-formatted-but-nonexistent account number failing safely with `404`, and a direct check that `resolve-recipient`'s response JSON contains no id/customerId/balance field. **22/22 checks passed, zero page or request errors.**

## What was deliberately not done

Rate limiting on `resolve-recipient` (recorded as accepted future hardening in ADR-005, since a fictional demo bank at this scale doesn't need it yet, but a production system would). Resolving a real customer display name for the recipient preview — would require a new account-service → customer-service call, an inter-service dependency this phase didn't introduce; the preview only shows what account-service itself actually knows, plus (for saved beneficiaries only) the sender's own saved label. Any interbank/external-IFSC support, Kafka, Outbox, Payment Switch, Transfer Service, Notification Service, Kubernetes — all explicitly out of scope per the task's own repeated instruction.
