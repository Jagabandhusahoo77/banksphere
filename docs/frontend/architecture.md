# Frontend Architecture

_Status: Phase 2 (banking experience), building on Phase 2A/2B's foundation, which built on the Phase 1 backend documented in [docs/architecture/application-architecture.md](../architecture/application-architecture.md) — this document covers the frontend specifically, in more depth than that file's summary._

## Two shells, one app

BankSphere's frontend is a single Vite/React SPA split into two visually and structurally distinct experiences that share the same codebase:

```text
Public site (unauthenticated)          Internet banking (authenticated)
────────────────────────────           ─────────────────────────────────
PublicLayout                           AppLayout
  PublicHeader (marketing nav)           BankingHeader (session, mobile menu)
  <page content>                         BankingSidebar (desktop) /
  Footer                                   MobileNavigation (mobile bottom tabs)
                                          <page content>
/, /about, /contact,                   /dashboard, /accounts, /accounts/:id,
/cards, /cards/:slug,                  /transactions, /banking/cards,
/loans, /loans/:slug,                  /banking/loans, /payments,
/deposits, /deposits/:slug             /investments, /profile, /support
                                        (all behind ProtectedRoute)

ChatbotWidget floats above both, mounted globally outside <Routes>.
```

`/login` renders standalone, outside both layouts — a focused sign-in screen is a deliberate choice (see [design-system.md](design-system.md) and [routing.md](routing.md)), not an oversight. See [routing.md](routing.md) for why the public product catalog and the authenticated placeholders both wanted `/cards`/`/loans` and how that collision was resolved.

## Data flow

Nothing changed about *where* data comes from in Phase 2 — the three Phase 1 backend services (customer/account/transaction) via the same `services/*Service.ts` modules — but *how pages consume it* changed:

```text
Page component
   │  useCustomer(customerId) / useAccounts(customerId) / useTransactions(accountId, page, size)
   ▼
hooks/use*.ts             ← new in Phase 2
   │  built on hooks/useAsync.ts (shared fetch/loading/error/cancelled-on-unmount logic)
   ▼
services/*Service.ts       ← unchanged from Phase 1
   │  axios call via apiClient.ts's per-service instances
   ▼
customer-service / account-service / transaction-service (Phase 1 backends, unchanged)
```

The Phase 1 discovery report identified the `useState`+`useEffect`+loading+error+cancelled-flag pattern duplicated identically across Dashboard/Accounts/Transactions. `hooks/useAsync.ts` extracts that pattern once; `useCustomer`/`useAccounts`/`useTransactions` are thin, typed wrappers around it. No page calls a service function directly inside its own `useEffect` anymore — see [components.md](components.md) for the full hook list.

**Explicitly not done:** no React Query, SWR, or other data-fetching library was introduced (per instruction) — `useAsync` has no caching, no request deduplication, and no background refetching. Each hook call is an independent fetch. This is a known, accepted limitation, not a partial implementation of something bigger.

## State management

Unchanged in shape from Phase 1, still the only global state:

- **`AuthContext`** — `customerId` (placeholder session, `localStorage`-backed), `login`, `logout`.
- **`ToastContext`** (new in Phase 2, `components/common/Toast.tsx`) — the second and last piece of app-wide state, a simple provider + `useToast()` hook for transient success/error notifications (e.g. "Deposit successful"). No toast state persists across a reload, by design.

Everything else is local component state (`useState`) or comes from the `use*` data hooks above. There is still no Redux/Zustand/global store, and — per instruction — none was added.

**`hooks/useInViewport.ts`** (new, Phase 3C) isn't state management in this sense — it's a small, local, per-component hook (like `useAsync`), not a shared store. It drives the homepage's one-time scroll-reveal animation and is the only new hook this phase added; see [components.md](components.md#hooks).

## API integration (unchanged architecture, extended usage)

`services/apiClient.ts`, `customerService.ts`, `accountService.ts`, `transactionService.ts` are the same Phase 1 files, not rewritten. Phase 2 pages now actually call the previously-unused `accountService.deposit()`/`accountService.withdraw()` from `AccountDetails.tsx` (see [routing.md](routing.md) for the route), closing the gap the Phase 1 discovery report flagged ("service functions exist but are unused"). `accountService.createAccount()` remains unused — no page builds an account-creation form this phase (see the "What was not built" section of the [engineering journal entry](../09-engineering-journal/)).

No new service functions were added. `customerService.updateCustomer()` was considered (Profile page) and deliberately *not* added — Profile is a "coming soon" placeholder per this phase's explicit scope, so there's no real caller for it yet.

## Responsive strategy

- **Breakpoints:** Tailwind's defaults (`sm` 640px, `lg` 1024px) are the only breakpoints used — no custom breakpoints were introduced. Layouts target 320px (smallest expected phone) through 1440px+ (desktop).
- **Navigation:** `PublicHeader` collapses to a slide-down mobile menu below `lg`. `AppLayout` uses `BankingSidebar` (persistent) above `lg` and `MobileNavigation` (fixed bottom tab bar) + a slide-in drawer below `lg` — see [components.md](components.md).
- **Tables:** `TransactionTable` wraps its `<table>` in a `.table-scroll` utility (`overflow-x-auto`, defined in `index.css`) rather than letting it overflow the page — the table scrolls horizontally within its own box on narrow viewports, the page itself never does.
- **Forms/cards:** grid layouts collapse from multi-column to single-column via `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3`-style utilities throughout; no fixed pixel widths outside of a handful of deliberately-bounded cases (a toast's max width, a truncated header label) — see the Phase 2 engineering journal entry for the specific audit performed.

## Product catalog data flow

Unlike account/customer/transaction data, card/loan/deposit/account/offer/insight/FAQ content has no backend at all — it's static, structured data shipped with the frontend bundle:

```text
Page component (CardsCatalog, CardDetail, LoansCatalog, ...)
   │  import { CARD_PRODUCTS, getCardBySlug } from "@/data/cards"
   ▼
data/{cards,loans,deposits,accounts,offers,insights,faqs}.ts    ← plain typed arrays, no fetch
```

This is deliberate, not a stand-in for a real API that got skipped — see [product-catalog.md](product-catalog.md) for the full content and the disclaimer requirements every rendering of it carries. If a real product-catalog backend is ever introduced, these files' shapes are the natural starting point for that API's response types, but nothing about them assumes that will happen.

**`data/navigation.ts` (new, Phase 3C) breaks this pattern on purpose** — it's navigation/UI configuration (`PRIMARY_NAV`, `QUICK_CATEGORIES`), not fictional product content, but was still placed under `data/` as a single source of truth consumed by three different components (`MegaMenuPanel`, `PublicNavDrawer`, `Footer.tsx`) that would otherwise each hardcode the same link targets independently — exactly the kind of drift that had already happened once (`Home.tsx`/`PublicHeader.tsx`/`Footer.tsx` each separately linking to the same protected routes pre-redesign). See [components.md](components.md) and [product-catalog.md](product-catalog.md).

## Chatbot data flow

Fully client-side, deliberately isolated from the rest of the app's state:

```text
ChatbotWidget (owns messages[], sending, open — local useState, not global)
   ▼
services/chatService.ts    ← mock only, no network call, no backend endpoint
```

Never touches `AuthContext`, never calls `apiClient.ts`'s axios instances, never reads real account data. Full design and the security boundary a real implementation must respect: [docs/chatbot/](../chatbot/).

## Known limitations carried into Phase 2

- Placeholder authentication is unchanged (still a customer-ID lookup, no password) — explicitly out of scope for this phase.
- `ProtectedRoute` now preserves the originally-requested route (`location.state.from`) and `Login` redirects back to it after sign-in — this was dead code in Phase 1 (the state was expected but never set) and is now wired correctly.
- No account-creation, transfer, card, loan, payment, or investment backend exists — the authenticated versions of those routes render an honest "coming soon" state (`ComingSoonPage`), never fabricated data or a form that pretends to submit somewhere real. The new public product pages (`/cards`, `/loans`, `/deposits`) are richer — real content, real calculators — but their "Apply"/"Open" CTAs are the same honesty pattern: a `Modal` explaining there's no application backend yet, not a fake success state.
- `LoanCalculator`/`FixedDepositCalculator` run on plain JS `number`s (no `BigDecimal` equivalent client-side without a new dependency) — see [product-catalog.md](product-catalog.md) for how display-time rounding keeps this from ever showing a floating-point artifact, and why this is acceptable for a fictional-product estimate but would not be for a real ledger value.
- The chatbot is a fully client-side mock with no LLM, no backend endpoint, and no external credentials — see [docs/chatbot/](../chatbot/) for the full current-vs-future breakdown.
