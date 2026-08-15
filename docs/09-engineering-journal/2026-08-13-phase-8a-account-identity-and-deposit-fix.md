# 2026-08-13 — Phase 8A: Account Identity (IFSC) + Deposit Workflow Fix

## Objective

Fix three reported issues from real portal testing: (1) confirm account numbers are properly server-generated, (2) add an IFSC code to the account model, (3) fix deposit, which was reported as "not working from the portal." Explicitly scoped to only these — no Kafka/Outbox/Payment Switch/Kubernetes/new major feature.

## Problem 1 — account number: verified, not rewritten

Inspected `AccountServiceImpl.generateUniqueAccountNumber()` before touching anything: it already generates a random 12-digit number (`String.format("%012d", ...)`) and loops on `accountRepository.existsByAccountNumber(...)` until it finds one that doesn't collide. `AccountCreateRequest` has no `accountNumber` field at all — there is no code path for a client to supply one. This already satisfied every stated requirement (server-side, unique, 12-digit, never client-supplied, stable after creation), so per the task's own instruction it was left alone. What was missing was proof: added `createAccount_assignsA12DigitServerGeneratedAccountNumber_neverSuppliedByTheClient` and `createAccount_retriesGeneration_whenTheFirstCandidateAccountNumberAlreadyExists` (the latter stubs `existsByAccountNumber` to return `true` once then `false`, and asserts the method is called twice — proving the retry-on-collision loop actually works, not just that it compiles).

## Problem 2 — IFSC added

BankSphere has no branch model, so per the task's own instruction this is a single constant, not a per-account or per-branch value: `AccountServiceImpl.BANKSPHERE_IFSC = "BANK0000001"` (public, so it's reusable from tests without duplicating the literal). Added:

- `Account.ifsc` (entity field, `NOT NULL`, matches the same 11-character format beneficiary-service already validates).
- `V2__add_ifsc_to_accounts.sql` — three-step migration (`ADD COLUMN` nullable → `UPDATE ... SET ifsc = 'BANK0000001' WHERE ifsc IS NULL` → `ALTER COLUMN ... SET NOT NULL` + a format `CHECK` constraint) specifically because the existing `V1` migration has already been applied and this project's live database already has real account rows in it — a bare `ADD COLUMN ifsc VARCHAR(11) NOT NULL` with no default would have failed outright against any existing row.
- `AccountResponse` gained an `ifsc` component. Since it's a Java record, this changed its constructor arity — found and fixed the one other call site (`AccountControllerTest.sampleResponse`) via a full `tsc`-equivalent inspection (`grep -rn "new AccountResponse("`), not by guessing.
- `AccountCreateRequest` was **not** touched — `ifsc` (like `accountNumber`) is never a request field.

**Verified against real, pre-existing data, not just a synthetic test.** Rather than fabricate an "existing rows" scenario, `mvn verify` was run against the project's actual shared `banksphere-postgres` container, which already held 33 real account rows accumulated from every prior phase's own testing (Phase 7A, Phase 8, this phase's own diagnostics). Flyway's log confirmed `Migrating schema "public" to version "2 - add ifsc to accounts"` / `Successfully applied 1 migration`, and a direct `psql` query afterward confirmed all 33 rows now have `ifsc = 'BANK0000001'` and zero have `NULL`. This is stronger evidence than a unit test could provide for "the migration is safe against existing data," because it *is* existing data, not a stand-in for it.

## Problem 3 — deposit: traced end-to-end, root cause found upstream of deposit itself

**This project had real browser tooling available for the first time** (`google-chrome`/`chromium` already installed in this environment) — every prior frontend phase's stated limitation ("no browser available") no longer applied. Used `puppeteer-core` (pointed at the system Chrome binary via `executablePath`, so no bundled-Chromium download was needed) from a throwaway scratchpad script — not added as a project dependency — to drive the actual compiled portal through a real user journey and capture real network requests/responses/console output/page errors, per the task's own explicit instruction to inspect real browser/network behavior rather than assume.

**First diagnostic run** (register → login → `/accounts`): revealed the account list was empty and there was no way to create one — `grep -rn "createAccount" frontend/src` showed `accountService.createAccount` was called from **nowhere** in the UI. This matches a gap `docs/00-project-overview/scope.md` had already documented since Phase 1/2 ("No real account-creation UI... `accountService.createAccount()` exists but no page calls it") — it had simply never mattered until a real user tried to walk the full journey.

**Second diagnostic run** (create an account directly via the real API, then drive the browser straight to that account's `/accounts/:id` page and submit a real deposit through the actual rendered form): the deposit succeeded perfectly. The captured network log showed a clean CORS preflight (`OPTIONS` → `200`), the real `POST http://localhost:8082/api/v1/accounts/{id}/deposit` with the exact headers and body `{"amount":10000}` a hand-written `curl` would produce, a `200` response with the updated balance, and the UI correctly re-rendering `₹10,000.00` plus the new `DEPOSIT` row in transaction history. Zero console errors, zero failed requests.

**Root cause: not a deposit bug.** The deposit code path — frontend request construction, JWT attachment, CORS, controller mapping, `AmountRequest` validation, ownership check, balance mutation, transaction-service recording, frontend refresh — was already fully correct, exactly as Phase 8's own live E2E test had already shown with `curl`. What was actually broken was one step earlier: nothing in the portal let a user *open* an account, so nobody testing "the deposit workflow" end-to-end through the UI could ever get far enough to reach a working deposit form. This is why the task's own explicit instruction — "do not assume where the problem is... trace the request... identify the root cause" — mattered: the naive fix (rewriting deposit code) would have changed nothing, since deposit was never the broken part.

**Fix**: built the missing account-creation UI. A modal on `Accounts.tsx` (reusing `Modal`/`Select`/`AmountInput`/`Button`, the same pattern `Beneficiaries.tsx` already established in Phase 8) asking only for account type, currency, and an optional initial deposit — never account number, IFSC, or customerId, which `AccountCreateRequest` has no fields for anyway. On success, it displays the real generated account number and IFSC from the backend's own response, then links to the new account's detail page.

## A second real, pre-existing bug found and fixed

While updating `types/account.ts` for `ifsc`, noticed `AccountCreateRequest`'s frontend type still had a `customerId: string` field — a leftover from before Phase 3A removed `customerId` from the backend's actual `AccountCreateRequest` (ADR-002, Decision 4). Harmless in practice (nothing called `createAccount` at all until this phase, and Jackson ignores unknown JSON properties by default), but a real, live drift between the TypeScript type and the real contract — exactly the kind of thing this phase's "never put customerId in a request the backend derives from the JWT" rule exists to prevent. Removed it.

## AccountDetails display

Redesigned the account-identity section of `/accounts/:id` to the requested layout: "BankSphere" (brand label) → Account Number (full, not masked — this is the account's own owner viewing their own data, same precedent as beneficiary-service's unmasked `BeneficiaryResponse`) with a one-click copy button → IFSC → Account Type → Available Balance → Status badge. The existing "Account ID" (UUID) copy section from Phase 8, used for the "transfer to another BankSphere account" flow, was kept underneath, unchanged.

## Tests

**Backend** (`account-service`): 6 new tests — `createAccount_assignsA12DigitServerGeneratedAccountNumber_neverSuppliedByTheClient`, `createAccount_assignsTheSingleBankSphereIfsc_neverSuppliedByTheClientAndNeverRandomized`, `createAccount_retriesGeneration_whenTheFirstCandidateAccountNumberAlreadyExists`, `createAccount_ignoresClientSuppliedAccountNumberIfscAndCustomerId_usingOnlyServerGeneratedValues` (a `@WebMvcTest` that POSTs a JSON body deliberately containing `accountNumber`/`ifsc`/someone-else's `customerId` and asserts the response and the captured service-layer argument both ignore all three). Existing `Account`/`AccountResponse` test fixtures across `AccountServiceImplTest`, `AccountControllerTest`, and both real-Postgres integration tests were updated to include `ifsc` (required for the integration tests specifically, since the column is now `NOT NULL` at the database level).

**Frontend**: `Accounts.test.tsx` gained 2 new tests (successful account creation asserts the exact `createAccount` payload has no `accountNumber`/`ifsc`/`customerId`, and displays the real generated values back; failed creation shows the backend's message without a fabricated success). `AccountDetails.test.tsx` gained 3 new tests (account number/IFSC display, friendly 403 and 500 error mapping via `getFriendlyErrorMessage`, extending the pattern Phase 8 established for Transfer/Beneficiaries to the deposit/withdraw form).

## Build and test results

- `mvn clean verify` (Docker `maven:3.9-eclipse-temurin-21`, against the live `banksphere-postgres`): **BUILD SUCCESS**. 48 tests total (18 controller + 28 service + 2 real-Postgres integration), all passing.
- `transaction-service`: `mvn clean test` re-run to confirm no regressions (untouched this phase) — 12/12 passing, unchanged.
- `npm run build` (`tsc -b && vite build`): clean, zero errors.
- `npm test` (`vitest run`): 34/34 passing (29 carried over from Phase 8 + 5 new).

## Real end-to-end result

Rebuilt and swapped both the `account-service` and `frontend` Docker images into the live stack. Ran the task's own 13-step scenario end-to-end **through the real compiled portal in headless Chrome** (not `curl` — an actual browser, driving actual rendered forms): register → log in → open a Savings account via the new UI → verify the displayed account number is 12 digits and genuinely server-generated → verify IFSC displays as `BANK0000001` → verify the new account starts at ₹0.00 → deposit ₹10,000 through the real form → verify the balance updates to ₹10,000.00 → verify the `DEPOSIT` transaction appears with a `+₹10,000.00` signed amount → verify the account number and IFSC are unchanged after the deposit. **All 13 steps passed.** The captured network log showed 16 real backend requests, zero non-2xx responses, zero console or page errors.

## What was deliberately not done

Transfer Service, Kafka, Outbox, Payment Switch, Notification Service, Kubernetes, and any frontend redesign beyond the Accounts/AccountDetails pages this fix required — all explicitly out of scope per the task's own repeated instruction.
