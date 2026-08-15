# ADR-005: Recipient Resolution by Account Number + IFSC

**Status:** Accepted for Phase 8B

## Context

Phase 7A's `POST /api/v1/accounts/transfer` identified the destination account by `destinationAccountId` — an internal UUID. Phase 8's frontend integration exposed exactly why that's wrong for a real customer-facing product: nobody knows another customer's internal database id. The Phase 8 Transfer page worked around this by asking the sender to paste the recipient's raw Account ID (with a "share this from your Account Details page" instruction) — technically functional, but not how any real banking product works, and explicitly flagged in that phase's own documentation as a gap to close.

This phase replaces that workaround with what a customer actually has: the recipient's **account number and IFSC** — matching real bank transfer forms (NEFT/IMPS/UPI-linked-account transfers all use exactly this pair). The internal account id becomes a pure implementation detail, never generated in the browser, never displayed, never required as input.

## Decision 1 — The public API identifies the destination by account number + IFSC, not UUID

`TransferRequest` changed from `{ sourceAccountId, destinationAccountId, amount, description }` to `{ sourceAccountId, destinationAccountNumber, destinationIfsc, amount, description }`. `TransferResponse` correspondingly dropped `destinationAccountId` in favor of `destinationAccountNumber` + `destinationIfsc`. `AccountServiceImpl.transfer()` resolves the destination account server-side, via the same `resolveDestinationAccount(accountNumber, ifsc)` helper `resolveRecipient` uses (see Decision 2) — one place, so "this recipient is valid" and "this recipient is who actually gets credited" can never drift apart.

This is a breaking API change to `POST /api/v1/accounts/transfer`, made deliberately (not additively/versioned) because Phase 7A's shape was never a real product contract — it was explicitly documented as the seam Phase 8B would need to close, and there is exactly one caller (this project's own frontend), so there's no external consumer to preserve compatibility for.

## Decision 2 — A separate `resolve-recipient` endpoint, not inline-only resolution

`POST /api/v1/accounts/resolve-recipient` lets the frontend verify a recipient (matching real bank "verify payee" UX) *before* the customer commits to a transfer — the wizard's Step 2 needs to show a recipient preview and block "Continue" until the recipient is actually valid, which the transfer endpoint alone can't provide without also moving money.

**Why not fold this into `POST /transfer` only:** the UX explicitly requires a distinct verify-then-review flow (see the task's own wizard spec), and building that on top of a "dry run" flag on the transfer endpoint would be more complex than a dedicated read-only endpoint, not less.

## Decision 3 — The internal account id is never returned to the frontend, deliberately

Both `ResolveRecipientResponse` and `TransferResponse` were designed to exclude any internal id for the destination account. This was a genuine design fork (see the task's own framing: "first inspect the project's existing security model and decide whether this endpoint should expose accountId to the frontend at all") — the alternative considered was a `resolve-recipient` that returns `{ accountId, accountNumber, ifsc, ... }`, with the frontend then submitting `destinationAccountId` to `/transfer`. Rejected because:

- It would recreate exactly the "the frontend now holds and passes around an internal UUID" pattern this phase exists to eliminate, just one step removed.
- The UUID serves no purpose to the frontend — everything the UI needs (masked account number, IFSC, bank name) is already in `ResolveRecipientResponse`.
- Keeping resolution *and* the final lookup both server-side, from the same business identifiers, means there is exactly one trust boundary (account number + IFSC → real account) instead of two (account number + IFSC → id, then id → real account, with the second hop trusting a value round-tripped through the browser).

`ResolveRecipientResponse` is therefore deliberately minimal: `{ accountNumber, ifsc, bankName }`. No `accountId`, no `customerId`, no `balance` — see Decision 4 for why that minimality also matters for enumeration safety.

## Decision 4 — Why `resolve-recipient` is not an account-enumeration vulnerability

The task's own instruction was explicit: don't build an arbitrary account-lookup endpoint without a strong reason, and don't let an authenticated user enumerate arbitrary accounts. This was taken seriously, not waved away:

- **No lookup-by-account-number endpoint exists in the sense the concern describes.** `resolve-recipient` requires *both* a 12-digit account number *and* a correctly-formatted IFSC in one request; it is not a bare `GET /accounts/{accountNumber}`.
- **The response leaks nothing beyond "this exact pair is a valid, active BankSphere account."** No customerId, no owner name, no balance, no account type. An attacker who successfully guesses a valid account number learns only that it exists and is active — not who owns it or anything about it financially. Compare this to a real bank's own payee-verification APIs (UPI, IMPS), which have the identical shape and the identical minimal-disclosure property, for the identical reason: the feature is unbuildable without *some* existence signal, so the mitigation is minimizing what that signal reveals, not eliminating it.
- **Account numbers are not sequential or otherwise guessable.** They're generated via `SecureRandom` over the full 12-digit space (`AccountServiceImpl#generateUniqueAccountNumber`, unchanged since Phase 1) — 10^12 possibilities, not an enumerable id space the way a small sequential integer would be.
- **Rate limiting is out of scope for this phase** (as it has been for every phase — see `docs/security/threat-model.md`'s existing "not built" list) and would be the natural production hardening on top of this, exactly as a real bank's payee-verification endpoint would need it. Recorded here as accepted future work, not silently omitted.

## Decision 5 — Same-bank-only, explicit rejection for any other IFSC

`resolveDestinationAccount` checks IFSC first (before any database lookup): if it doesn't case-insensitively equal `AccountServiceImpl.BANKSPHERE_IFSC`, the request fails with `UnsupportedIfscException` → `422`, distinct from `RecipientNotFoundException` → `404` (a real-looking BankSphere account number that doesn't exist). These are deliberately different failure modes with different messages — "we don't support that bank yet" is a different customer story than "check the account number," and collapsing them would be less honest, not more secure (IFSC prefixes are public information, not a secret whose confirmation would leak anything).

## Decision 6 — Beneficiary integration re-verifies at transfer time, never trusts the saved snapshot

A saved `Beneficiary` record (beneficiary-service, Phase 6) is a point-in-time snapshot the *sender* created — the underlying account could have been closed since. Selecting a beneficiary in the Transfer wizard triggers the exact same `resolve-recipient` call a manually-typed account number would, using the beneficiary's stored `accountNumber`/`ifsc`. This means:

- An `INACTIVE` beneficiary is filtered out of the picker entirely (the wizard only lists `status === "ACTIVE"` beneficiaries) — matching "if the beneficiary is inactive/deleted, do not allow the transfer."
- Even an `ACTIVE` beneficiary whose underlying account has since been closed fails resolution with the same `422`/`404` a stale manual entry would — the beneficiary record is a convenience for *filling in* the account number/IFSC, never a bypass of verification.
- Beneficiary-service itself is unmodified — it continues to store only `accountNumber`/`ifsc` (business identifiers), never an account-service UUID, so the two services stay decoupled exactly as ADR-003 established.

## Decision 7 — Ledger descriptions now use account numbers, not internal ids (bonus correction)

Phase 7A's default ledger-leg description text was `"Transfer to account " + destination.getId()` (an opaque UUID). While rewriting `transfer()` for account-number-based resolution, this was changed to `"Transfer to account " + destination.getAccountNumber()` — a real customer reading their own transaction history via `GET /transactions/account/{id}` now sees a recognizable account number instead of a meaningless UUID. This is a pure improvement with no compatibility concern (the frontend's `utils/transactionDirection.ts` heuristic only matches the `"Transfer to account "`/`"Transfer from account "` *prefix*, unaffected by what follows it).

## Consequences

- `POST /api/v1/accounts/transfer`'s request/response shapes changed (breaking, single-caller — see Decision 1). All existing tests, both real-Postgres integration tests, and the frontend were updated in lockstep within this same phase.
- The Transfer wizard's "Recipient" step now has three genuinely distinct, clearly-labeled paths (My accounts / Saved beneficiary / Account number) instead of Phase 8's "own account vs. paste-a-UUID" split — closing the exact confusion this phase was opened to fix.
- No backend endpoint anywhere in this codebase now accepts or returns an internal account id from/to a context representing *another* customer's account — the only ids ever crossing the API boundary belong to the resource's own owner (see `docs/security/authorization.md`).

## Deferred to a later phase

A dedicated `auditLog`/rate-limit on `resolve-recipient` (production hardening, not needed for a fictional demo bank at this scale); resolving a customer's real display name for the recipient preview (would require account-service to call customer-service, a new inter-service dependency not introduced in this phase — the preview instead honestly shows only what account-service actually knows: masked account number, IFSC, "BankSphere," and — only for saved beneficiaries — the sender's own saved label for that person); any interbank/external-IFSC transfer capability (Kafka, a payment switch, NEFT/RTGS/IMPS simulation) — explicitly out of scope, per the task's own repeated instruction.
