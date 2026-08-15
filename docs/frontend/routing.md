# Routing

_Status: Phase 3C (public website redesign). Route table defined in `frontend/src/App.tsx`._

## Full route table

```text
Public (PublicLayout: PublicHeader + Footer)
  /                    Home
  /about               About
  /contact             Contact
  /cards               CardsCatalog     (public product catalog + comparison)
  /cards/:slug         CardDetail       (platinum | cashback | travel | rewards | debit)
  /loans               LoansCatalog     (public product catalog + EMI calculator)
  /loans/:slug         LoanDetail       (home | personal | car | education)
  /deposits            DepositsCatalog  (public product catalog + returns calculator)
  /deposits/:slug      DepositDetail    (fixed | recurring)
  /savings-account     SavingsAccountDetail  (real page — see "Closing the public-page gap" below)
  /help                Help             (real page — FAQAccordion + FAQS + contact CTA)
  /business            Business         (honest ComingSoonPage — Business mega-menu target)
  /nri                 NRI              (honest ComingSoonPage — NRI mega-menu target)
  /premium-banking     PremiumBanking   (honest ComingSoonPage — Premium Banking mega-menu target)
  /account-types       AccountTypes     (honest ComingSoonPage — Current/Salary accounts)
  /wealth              Wealth           (honest ComingSoonPage — Mutual Funds/Bonds)
  /insurance           Insurance        (honest ComingSoonPage)

Standalone (no shared layout)
  /login               Login
  /register            Register

Authenticated (ProtectedRoute → AppLayout: BankingHeader + BankingSidebar/MobileNavigation)
  /dashboard           Dashboard
  /accounts            Accounts
  /accounts/:id        AccountDetails   (deposit/withdraw forms)
  /transactions        Transactions
  /transfer            Transfer         (Phase 8 — real, backend-integrated multi-step transfer; recipient flow redesigned Phase 8B)
  /beneficiaries       Beneficiaries    (Phase 8 — real beneficiary-service CRUD; new in this phase)
  /kyc                 Kyc              (Phase 9C — real kyc-service application/document/status flow)
  /banking/cards       Cards            (coming soon — manage your own cards)
  /banking/loans       Loans            (coming soon — manage your own loans)
  /payments            Payments         (coming soon)
  /investments         Investments      (coming soon)
  /profile             Profile          (coming soon)
  /support             Support          (coming soon)

Catch-all
  *                    NotFound

Global, outside <Routes>
  <ChatbotWidget />    Floating support chat button — rendered on every route
```

## Phase 9D — no new routes; OTP/step-up UI reuses existing surfaces

Deliberately no new top-level route. Customer OTP login is a "Password" / "One-time code" tab on the existing `/login` page (`pages/auth/Login.tsx`) — not a separate `/login/otp` route — since it's the same authentication step, just a different second factor, and splitting it into its own URL would fragment the "already-authenticated → redirect away from /login" logic `Login.tsx` already has. Step-up authentication for a transfer is a `StepUpOtpModal` opened from within the existing `/transfer` flow when account-service returns `403` — not a route either, since it's a transient confirmation step inside an in-progress operation, not a page a customer would ever navigate to or bookmark directly (the same reasoning `ComingSoonPage`/`Modal` already follow for other in-flow confirmations). See [docs/architecture/customer-authentication.md](../architecture/customer-authentication.md).

## Phase 9C — `/kyc` added

A new protected route, `/kyc` → `pages/kyc/Kyc.tsx`, added alongside the existing `/beneficiaries` entry (same `AppLayout` block). One state-driven page (not a wizard with sub-routes) covering the full application lifecycle — no application yet, `DRAFT`/`ADDITIONAL_INFORMATION_REQUIRED` (document upload), `SUBMITTED`/`UNDER_REVIEW`/`RESUBMITTED` (status banner), `APPROVED`/`REJECTED` (terminal). See [docs/architecture/customer-360-and-kyc.md](../architecture/customer-360-and-kyc.md).

## Phase 8 — `/transfer` and `/beneficiaries` became real

`/transfer` was a `ComingSoonPage` through Phase 3C; Phase 8 replaced it with a real multi-step form wired to `POST /api/v1/accounts/transfer` (Phase 7A). `/beneficiaries` is an entirely new route/page in this phase, wired to `beneficiary-service` (Phase 6) — it didn't exist in any prior phase's route table or nav.

**A genuine cross-service gap surfaced during this work, not invented as a frontend workaround:** a saved beneficiary only stores `accountNumber`/`ifsc`/`bankName` (beneficiary-service's own model), but `POST /api/v1/accounts/transfer` required a real BankSphere `destinationAccountId` (a UUID), and no account-service endpoint resolved one from the other (no accountNumber-based account lookup existed — deliberately, since exposing one carelessly could enable account enumeration). Phase 8's stopgap: the Transfer page's "another BankSphere account" path asked the sender to paste the recipient's Account ID directly (surfaced via a copy button on `/accounts/:id`), and a saved beneficiary's details were shown for reference only, without resolving to a destination account. **This gap was closed properly in Phase 8B** — see below.

`/` no longer redirects to `/dashboard` — it's a real public homepage (a Phase 2A/2B change, unchanged this phase).

## Phase 8B — `/transfer` recipient flow redesigned around account number + IFSC

Phase 8's "paste the recipient's Account ID (UUID)" field is gone. Account-service gained `POST /api/v1/accounts/resolve-recipient`, which verifies a recipient by `accountNumber` + `ifsc` and returns only `{ accountNumber, ifsc, bankName }` — no internal id, no enumeration-useful data (see [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md)). `/transfer`'s Step 2 ("Recipient") now has three tabs:

- **My accounts** — one of the caller's own other accounts; resolved locally from data already fetched (no extra API call).
- **Saved beneficiary** — only `ACTIVE` beneficiaries are listed; selecting one calls `resolve-recipient` with the beneficiary's stored `accountNumber`/`ifsc` to re-verify it's still a real, active account (a beneficiary record is a convenience for autofill, never a bypass of verification).
- **Account number** — the sender types the recipient's 12-digit account number + 11-character IFSC directly and clicks "Verify recipient," which calls `resolve-recipient`.

All three paths converge on a `resolvedRecipient` state that gates a "Recipient" preview card (masked account number, IFSC, bank name, and — for beneficiary/own-account selections only — a name; the raw account-number path shows no fabricated name, since none exists) and gates the wizard's "Continue" button. The transfer is then submitted as `{ sourceAccountId, destinationAccountNumber, destinationIfsc, amount, description }` — the internal account id is never generated in, held by, or sent from the browser, in either direction.

## Closing the public-page gap (Phase 3C)

Before this phase, `/accounts`, `/investments`, `/payments`, and `/support` only existed as **protected** pages — there was no public page describing these products/services to an anonymous visitor. This was a latent gap in Phase 1/2 that this phase's new Personal mega-menu and homepage "Quick Banking Categories" row would otherwise have made much more visible (linking straight into a login wall for a *product* page reads as broken; a login wall for an *action* — Transfer, Payments, Service Requests — is normal, expected bank-website behavior and needs no fix).

**Resolution, same pattern as the existing `/cards` vs `/banking/cards` collision writeup below:** rather than reuse the protected paths or silently drop the new nav entries that would have pointed at them, this phase added distinct public pages/paths:

- `/savings-account` — a **real** page (`ProductDetailLayout` + `data/accounts.ts`, same pattern as `CardDetail`/`LoanDetail`/`DepositDetail`), since BankSphere's savings-account concept is real enough to describe properly and only one variant (Savings) has actual illustrative data.
- `/account-types`, `/wealth`, `/insurance` — honest `ComingSoonPage`s, since Current/Salary accounts, Investments (Mutual Funds/Bonds), and Insurance have no backend product behind them yet.
- `/help` — a **real** page recomposing the already-real `FAQAccordion`/`FAQS` content, rather than a stub, since a brand-new "Help & Support" primary nav item landing on a login wall would be a particularly bad first impression.

None of this changes the protected `/accounts`, `/investments`, `/payments`, or `/support` routes — they're untouched, and still exactly where a signed-in customer expects them.

## Business/NRI/Premium Banking (Phase 3C)

`/business`, `/nri`, `/premium-banking` are the mega-menu landing targets for those three primary-nav segments. All three are `ComingSoonPage`s — BankSphere has no real business/NRI/premium banking products or backend, and fabricating fictional product catalogs for them (rates, eligibility, documents) was scoped out of this phase (see [homepage-design.md](homepage-design.md) and the Phase 3C engineering journal entry). Every item inside those three mega menus routes to the one corresponding page for its segment, rather than each getting its own near-identical stub.

## Resolving the `/cards` and `/loans` naming collision

This phase's brief asked for public product-catalog pages at `/cards` and `/loans` — but those exact paths already existed as **authenticated** "coming soon" placeholders (added in Phase 2A/2B, for a future "manage your own cards/loans" screen). Both can't own the same path.

**Resolution:** the public product catalog keeps the natural `/cards` and `/loans` paths (they're the primary, content-rich pages a marketing site needs at those URLs). The authenticated placeholders — which had zero real functionality either way, just a "coming soon" state — moved to `/banking/cards` and `/banking/loans`. This is a frontend-only route rename with no backend impact and no loss of functionality (there wasn't any to lose), applied specifically to resolve a genuine collision this phase's own requirements introduced. `BankingSidebar.tsx`'s nav entries were updated to match; `PublicHeader.tsx`/`Footer.tsx`'s "Cards"/"Loans" links were left pointing at the (correct, now-real) public `/cards`/`/loans` — which also incidentally fixes a Phase 2A/2B issue where a logged-out visitor clicking "Cards" in the header would have been bounced straight to `/login` for what was only ever a stub.

## Product detail routes (`:slug`)

`/cards/:slug`, `/loans/:slug`, `/deposits/:slug` all follow the same pattern: `useParams<{ slug: string }>()` → `getXBySlug(slug)` (see [product-catalog.md](product-catalog.md)) → if found, render `ProductDetailLayout` with that product's data; if not found, render `<NotFound />` directly (not a redirect) — so `/cards/not-a-real-card` behaves like any other bad URL rather than crashing or silently falling through.

## Protecting authenticated routes

`routes/ProtectedRoute.tsx` wraps the `AppLayout` route group. If `AuthContext.customerId` is unset, it redirects to `/login`, passing the originally-requested path (and query string) as router state (`state={{ from }}`), and `Login.tsx` redirects back there after sign-in — see the Phase 2A/2B journal entry for how this closed a dead-code gap. Unchanged this phase.

## 404 handling

The catch-all renders a real `NotFound` page — it does not redirect. Unchanged this phase, and now also used directly (not via redirect) by the three product-detail routes above for an invalid slug.

## Why `/login` sits outside both layouts

Unchanged from Phase 2A/2B — see that phase's reasoning. A sign-in screen gets a focused, distraction-free presentation rather than the full marketing header/footer or the authenticated sidebar shell.

## The chatbot isn't a route

`ChatbotWidget` is rendered once, globally, as a sibling of `<Routes>` in `App.tsx` — not tied to any particular page. It appears on every route, public and authenticated alike, including `/login`.

## What's intentionally not routed as "real" functionality

`/cards/:slug`, `/loans/:slug`, `/deposits/:slug`, `/savings-account`'s "Apply"/"Open"/"Open a Savings Account" CTAs open a `Modal` with a Coming Soon message rather than navigating anywhere or submitting anything — there is no application/account-opening backend. Similarly `/banking/cards`, `/banking/loans`, `/payments`, `/investments`, `/profile`, `/support`, `/transfer`, `/business`, `/nri`, `/premium-banking`, `/account-types`, `/wealth`, `/insurance` all render `ComingSoonPage`. `InsightCard`'s "Read article" CTA (homepage Latest Insights section) opens the same kind of Modal — there is no article backend either. None of these fake a working flow — see [docs/00-project-overview/scope.md](../00-project-overview/scope.md) for the full list of what's genuinely implemented versus honestly deferred.
