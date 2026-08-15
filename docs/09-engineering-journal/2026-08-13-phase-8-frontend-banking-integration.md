# 2026-08-13 — Phase 8: Frontend Banking Portal Integration

## Objective

Connect the existing BankSphere frontend to the real backend APIs so banking operations (deposit, withdraw, transfer, beneficiary management, transaction history) work end-to-end from the UI. Explicit constraints: no fake/mock data, no hard-coded balances, no fabricated success states, no bypassing the backend, no backend rewrites — and don't rebuild pages that already worked.

## Inspection before implementation

Read the full existing frontend before touching anything: `App.tsx`'s route table, `services/apiClient.ts` and every `*Service.ts`, `context/AuthContext.tsx`, `types/{account,transaction}.ts`, every hook under `hooks/`, `pages/{dashboard,accounts,transactions,transfer}/*.tsx`, and the relevant shared components (`Button`, `Input`, `Select`, `AmountInput`, `Modal`, `Toast`, `EmptyState`, `ErrorState`, `Skeleton`, `Icon`, `Card`, `Badge`, `TransactionTable`/`TransactionRow`). Also re-read the actual backend DTOs/controllers directly (`AccountController`, `TransferRequest`/`TransferResponse`, `BeneficiaryController`, `CreateBeneficiaryRequest`/`UpdateBeneficiaryRequest`/`BeneficiaryResponse`, `TransactionResponse`, `PageResponse`) rather than assuming field names.

**Key finding: most of "the integration" already existed.** Dashboard, Accounts, Transactions, and `AccountDetails`' deposit/withdraw forms were already genuinely wired to real backend calls since Phase 2A/2B — `useAsync`/`useAccounts`/`useTransactions` all do real HTTP requests, and `TransactionTable`'s "No transactions yet" is `EmptyState` rendered only when `!loading && transactions.length === 0`, not static placeholder content. Registering two customers and depositing into one live-confirmed the Transactions page correctly shows a genuinely-empty history as empty and a genuinely-populated one as populated — the reported bug was, on inspection, not a bug in the code that was there. What had **zero** frontend integration: Phase 7A's transfer endpoint (`Transfer.tsx` was a `ComingSoonPage`) and beneficiary-service (no beneficiary type, service, hook, page, or route existed anywhere — confirmed explicitly out of scope by beneficiary-service's own Phase 6 journal entry).

## What was built

- **`services/beneficiaryService.ts`** (new) + **`beneficiaryApiClient`** (new, `apiClient.ts`) + **`VITE_BENEFICIARY_SERVICE_URL`** wired through `vite-env.d.ts`, `.env.example`, `Dockerfile`, and `docker-compose.yml`'s frontend build args/`depends_on` — full CRUD (`getBeneficiaries`/`getBeneficiary`/`createBeneficiary`/`updateBeneficiary`/`deactivateBeneficiary`), same one-object-of-async-functions shape as every existing service file.
- **`accountService.transfer()`** (new function on the existing service, not a new file) — `POST /api/v1/accounts/transfer`, since transfer lives in account-service itself.
- **`types/beneficiary.ts`** (new) and `TransferRequest`/`TransferResponse` added to `types/account.ts` — both typed directly from the real backend DTOs, not guessed.
- **`hooks/useBeneficiaries.ts`** (new) — identical `useAsync`-wrapping convention as every other hook.
- **`pages/transfer/Transfer.tsx`** (rewritten from a `ComingSoonPage`) — a real 4-step wizard (source account → recipient → amount/description → review) ending in a result screen. Prevents double submission (`if (submitting) return` guard plus the `Button`'s own `loading`-driven `disabled`, verified with a rapid-triple-click test). Never computes a balance client-side — after a successful transfer it calls `reloadAccounts()` to re-fetch real balances from account-service, and never marks a transfer successful until the backend actually responds `200`. Shows the backend's own `transferId`, explicitly labeled as account-service's own correlation id, not a transaction-service `TXN-...` reference (per ADR-004's terminology).
- **`pages/beneficiaries/Beneficiaries.tsx`** (new) + `/beneficiaries` route + `BankingSidebar` nav entry — list/add/edit/deactivate, friendly client-side validation mirroring the backend's Bean Validation rules (non-blank, account-number digit pattern, IFSC pattern) while the backend remains authoritative (a real 400/409 from the API is still shown, not silently swallowed).
- **`utils/apiError.ts`** (new) — `ApiError` (still a plain `Error` subclass, so every existing `err instanceof Error ? err.message : ...` call site keeps working unchanged) additionally carries the real HTTP `status` and the backend's `details` array; `getFriendlyErrorMessage()` maps status codes to the consistent 401/403/404/409/422/500/network copy the task asked for, with per-call overrides for operation-specific wording.
- **`utils/transactionDirection.ts`** (new) + `TransactionRow.tsx`/`Dashboard.tsx` updated to use it — see "A real backend gap" below.
- **`AccountDetails.tsx`**: added a small "Account ID" row with a copy-to-clipboard button — see "A real backend gap" below for why this was necessary, not decorative.

## A real backend gap, discovered and documented rather than worked around

`Beneficiary` stores `accountNumber`/`ifsc`/`bankName`; `POST /api/v1/accounts/transfer` requires a real BankSphere `destinationAccountId` (UUID). **No account-service endpoint resolves one from the other** — there is no accountNumber-based account lookup at all, and adding one would itself be a security-relevant new capability (account enumeration) that this frontend-only phase has no mandate to add to the backend. This means "select a saved beneficiary and transfer to them" cannot be made to actually work end-to-end without a backend change.

Rather than fabricate a resolution (e.g., silently guessing an id, or pretending the accountNumber field could be reused as one), the Transfer page implements two paths that are both fully real:
1. **"One of my own accounts"** — fully automatic, using `getAccountsByCustomer` (a real, already-owned list).
2. **"Another BankSphere account"** — the sender enters the recipient's actual Account ID directly. This required adding a way to *find* that ID in the first place, since `AccountDetails.tsx` previously only ever showed the masked account *number* — added a copyable "Account ID" field there for exactly this purpose.

Selecting a saved beneficiary in the "Another BankSphere account" path shows their name/bank/account-number for reference (real data) and pre-fills the description, but does **not** claim to fill in `destinationAccountId` — the UI says explicitly that this isn't possible yet and why. **Recommended backend follow-up** (not implemented, out of scope for this phase): either a scoped "resolve my own beneficiary's account" endpoint in beneficiary-service that itself calls account-service server-side (avoiding a client-facing enumeration surface), or have beneficiary creation optionally validate/capture a real BankSphere account id up front when the beneficiary is a BankSphere customer.

## A second real gap: TRANSFER ledger rows have no direction field

`TransactionResponse` has no direction/counterparty field, and a transfer's two ledger legs aren't linked by a shared id (ADR-004's own documented limitation). The only signal available is account-service's *default* per-leg description text ("Transfer to account `<id>`" / "Transfer from account `<id>`") — which is **only present when the sender didn't supply a custom description**. If they did, both legs carry the identical custom text and direction genuinely cannot be determined.

`utils/transactionDirection.ts` implements this as a best-effort heuristic with an honest neutral fallback (no guessed sign, label reads plain "TRANSFER" rather than a wrong "IN"/"OUT") rather than guessing. **This was directly confirmed live**: the end-to-end smoke test below deliberately passed a custom description ("Phase 8 E2E transfer"), and both the sender's and recipient's copies of that transaction show the identical description — exactly the ambiguous case the fallback exists for. Recommended backend follow-up: add a `direction` field (or a shared `transferId`) to `TransactionResponse` so this stops being a frontend heuristic.

## A real bug found and fixed along the way

While testing the Beneficiaries "add" form, typing into the modal's inputs only ever registered the first keystroke. Root cause: `Modal.tsx`'s single `useEffect` (focus-on-open, Escape-to-close, body-scroll-lock) listed `onClose` as a dependency; `Beneficiaries.tsx` (like most callers) passes an inline arrow function as `onClose`, a new reference on every render — and the form re-renders on every keystroke. Every re-render re-ran the effect, which calls `dialogRef.current?.focus()`, stealing focus straight back out of whatever input the user was typing into. This is a **pre-existing latent bug**, not something this phase introduced — it simply hadn't been hit before because no prior page had put a real form inside a `Modal`. Fixed by reading `onClose` through a ref inside the effect and dropping it from the dependency array, so the effect (and the focus call) only fires when `open` itself actually changes. Documented in `docs/frontend/components.md`.

## Testing

No test framework existed before this phase (confirmed: no `vitest`/`jest`/`@testing-library/*` dependency, no `*.test.*` file anywhere, no `test` script). Added Vitest + React Testing Library + `@testing-library/jest-dom` + `@testing-library/user-event` + `jsdom` as the minimum infrastructure genuinely required to fulfil the task's own explicit test list — the same judgment call as adding `maven-failsafe-plugin` in Phase 7A. Configured via `vite.config.ts`'s `test` block and `src/test/setup.ts`.

Two real setup bugs were hit and fixed before the suite was trustworthy: (1) RTL's automatic per-test `cleanup()` never registered itself, because Vitest `globals` mode is deliberately off in this project (test files import `describe`/`it`/`expect`/`vi` explicitly) — fixed with an explicit `afterEach(() => cleanup())` in `setup.ts`; (2) module-level `vi.fn()` mocks are shared across every test in a file with no automatic reset, so call counts and queued `mockResolvedValueOnce` values leaked between tests — fixed with a global `afterEach(() => vi.resetAllMocks())`. Both are exactly the kind of invisible-until-you-hit-them issues a from-scratch test setup runs into; recorded here rather than glossed over.

29 tests across 6 files, all passing:
- `utils/apiError.test.ts` (10) — status-to-message mapping for 401/403/404/409/422/500/network/generic.
- `pages/accounts/Accounts.test.tsx` (2) — real load, genuine empty state.
- `pages/transactions/Transactions.test.tsx` (3) — genuine empty state (not shown while loading), real data rendering, error state (not empty state) on fetch failure.
- `pages/accounts/AccountDetails.test.tsx` (4) — deposit amount validation (rejects zero without calling the backend), successful deposit refreshes both the account and transaction-history fetchers, withdrawal amount validation, backend rejection message surfaced verbatim (insufficient balance).
- `pages/transfer/Transfer.test.tsx` (5) — source-account-required validation, amount validation, full happy path (asserts the exact `transfer()` payload, the result screen, and that accounts are re-fetched — proving balance refresh comes from the backend, not arithmetic), double-submission prevented (three rapid clicks → exactly one `transfer()` call), backend rejection shown without a fabricated success screen.
- `pages/beneficiaries/Beneficiaries.test.tsx` (5) — real load, genuine empty state, successful creation (exact payload asserted), client-side IFSC validation blocks submission before the API is called, backend 409 (duplicate) shown with a friendly message.

`npm run build` (`tsc -b && vite build`): clean, zero errors, on the second attempt (first attempt was before the Modal fix and before two test-file bugs were corrected — see above). `npm test` (`vitest run`): 29/29 passing.

## Live end-to-end verification

No browser is available in this environment (same constraint as every prior frontend phase). The Vitest/RTL suite above is a meaningfully stronger substitute than any prior phase had (real component rendering and real user interaction in `jsdom`), but to verify the actual backend integration end-to-end, a scripted `curl` sequence was run against the live Docker stack, calling the exact same endpoints with the exact same payload shapes the new frontend code calls, in the same order as the task's own 17-step scenario:

1. Register + log in Customer A. 2. Open an account. 3. Deposit ₹10,000. 4. Verify balance = ₹10,000. 5. Verify the deposit appears in Transactions. 6. Register + log in Customer B. 7. Open B's account (₹2,000 initial deposit). 8. A adds B as a beneficiary (`POST /api/v1/beneficiaries`, `201`). 9. A transfers ₹5,000 to B's real account id. 10. Verify `200` with a real `transferId`. 11. Verify A's balance = ₹5,000. 12. Verify A's transaction history shows the outgoing `TRANSFER`. 13. Verify B's balance = ₹7,000. 14. Verify B's transaction history shows the incoming `TRANSFER`. 15. Attempt a transfer of ₹999,999 (exceeds A's balance). 16. Verify `422` rejection. 17. Verify A's balance is still ₹5,000, unchanged.

All 17 steps passed exactly as expected. The frontend Docker image was also rebuilt with the new code and swapped into the running stack; confirmed it serves (`200` on `/`, real `<title>`, JS bundle loads) — this proves the build artifact itself works, though clicking through the actual rendered pages in a real browser was not possible here, consistent with every prior phase's stated limitation.

## What was deliberately not done

Kafka, an Outbox pattern, a payment switch, Kubernetes, any backend rewrite — all explicitly out of scope per the task's own repeated instruction. The beneficiary→destinationAccountId resolution gap and the TRANSFER-direction ledger gap were both identified and documented, not silently worked around or ignored.
