# Component Library

_Status: Phase 9D (customer OTP + step-up authentication), building on Phase 8 (banking portal backend integration), Phase 3C (public website redesign), Phase 2A/2B, and Phase 2 (banking experience). All paths relative to `frontend/src/`._

## `components/common/` — generic, domain-agnostic

| Component | Purpose |
|---|---|
| `Icon.tsx` | Self-authored inline SVG icon set (~30 icons, 24×24, `stroke="currentColor"`) exposed as `<Icon name="wallet" />`. No icon-library dependency was added. **Phase 8**: added `arrow-down-left` (incoming-transfer direction) and `users` (beneficiaries). |
| `Button.tsx` | 5 variants (`primary`/`secondary`/`outline`/`ghost`/`danger`) × 3 sizes, with `loading` (spinner + `aria-busy`), `icon`/`iconPosition`, `fullWidth`. |
| `Input.tsx` | Text input with label, hint, error, optional `leadingText` adornment; wires `aria-invalid`/`aria-describedby` automatically. |
| `Select.tsx` | Same label/hint/error contract as `Input`, styled `<select>` with a custom chevron. |
| `Badge.tsx` | Small pill for status (`success`/`warning`/`error`/`info`/`neutral`/`brand` tones) — used for account and transaction status. |
| `Card.tsx` | Enhanced from the Phase 1 version: optional `title`/`subtitle`/`action` header slot, `padding` variant, `interactive` hover elevation. Phase 1's `Card` and `StatusBanner` files were removed (not kept alongside this one — see the engineering journal's note on avoiding duplicates). |
| `Modal.tsx` | Accessible dialog: `role="dialog"`, `aria-modal`, Escape-to-close, backdrop-click-to-close, focus moved in on open and restored on close, body scroll locked while open. **Now has a real caller**: `ProductDetailLayout`'s "Apply"/"Open a Fixed Deposit" CTAs open it with a Coming Soon message, since no application backend exists — see [routing.md](routing.md). **Bug fixed in Phase 8**: the focus/Escape/scroll-lock effect depended on `onClose`, so a caller passing an inline `onClose` (any form inside the modal, e.g. `Beneficiaries.tsx`'s add/edit dialog) re-ran the effect — including re-stealing focus via `dialogRef.current?.focus()` — on every keystroke. Fixed by reading `onClose` through a ref inside the effect instead of listing it as a dependency, so the effect only re-runs when `open` itself changes. |
| `Toast.tsx` | `ToastProvider` + `useToast()` — lightweight, no dependency added. Mounted once in `main.tsx`. |
| `Skeleton.tsx` | `Skeleton`, `SkeletonText`, `SkeletonCard` — loading placeholders used instead of a blank screen or a bare spinner during data fetches. |
| `EmptyState.tsx` | Illustration + title + description + optional action, for "no data" states (no accounts, no transactions). |
| `ErrorState.tsx` | Illustration-free error block (icon + message + optional "Try again" button wired to a hook's `reload()`). |
| `Spinner.tsx` | Small inline spinner with a screen-reader-only label, for places `Skeleton` doesn't fit. |
| `ComingSoonPage.tsx` | Shared shell for placeholder feature pages — illustration, description, a "what's planned" list, and links back to Dashboard/Support. Originally authenticated-app-only (`/banking/cards`, `/banking/loans`, Payments/Investments/Profile/Support, `/transfer`); Phase 3C is its first use on the **public** site too (`/business`, `/nri`, `/premium-banking`, `/account-types`, `/wealth`, `/insurance`), reused unchanged — same component, both contexts. |
| `FAQAccordion.tsx` | Expandable Q&A list — `aria-expanded`/`aria-controls` on each `<button>`, a `role="region"` panel, native keyboard support (no custom key handling needed for a `<button>`). Used on the homepage FAQ section and on every `ProductDetailLayout` page, both fed by `data/faqs.ts`. |
| `StepUpOtpModal.tsx` | **New, Phase 9D.** Reusable step-up-authentication dialog built on `Modal.tsx` — requests a fresh challenge on open (bound to the exact operation the caller describes via `transferContext`), shows an `operationSummary` (e.g. "Send ₹1,00,000 to account ending 4821"), verifies the entered code, and hands the verified `challengeId` back via `onVerified`. Never calls a banking endpoint itself — the caller (`Transfer.tsx`) is responsible for retrying the actual operation, the same intent-vs-execution boundary `docs/chatbot/security.md` already establishes for the chatbot. Currently used only by `Transfer.tsx`, written generically enough to be reused by a future withdrawal/beneficiary-creation step-up flow without changes. |
| `DevOtpInboxPanel.tsx` | **New, Phase 9D.** Local-development-only collapsible panel showing recently delivered OTP codes, with an optional `onSelectOtp` callback to fill a code directly into the caller's input. Renders nothing (`null`) unless `import.meta.env.DEV` — a production `vite build` strips it entirely, not just hides it — and even then, the backend route it calls independently 404s outside a dev profile (two independent gates). Used on the Login page's OTP-code step and inside `StepUpOtpModal`. |

## `components/navigation/`

| Component | Purpose |
|---|---|
| `Logo.tsx` | Wraps the branding SVGs; `variant="default"\|"white"`, links to `/` by default (override via `to`). |
| `PublicHeader.tsx` | **Rewritten in Phase 3C.** Marketing site header: desktop nav row driven by `data/navigation.ts`'s `PRIMARY_NAV`, hover/click-opened `MegaMenuPanel` per item with a mega menu, `PublicNavDrawer` on mobile, Log in / Open an account CTAs. Closes any open menu on `Escape`, outside click, or route change. |
| `MegaMenuPanel.tsx` | **New, Phase 3C.** Desktop mega-menu dropdown — full-width panel positioned under the header (`position: sticky` already establishes a containing block for its `absolute` positioning, no extra `relative` needed), renders `NavColumn[]` from `data/navigation.ts` in a fixed-class grid (never a dynamic `grid-cols-${n}` string — same rule as `ProductDetailLayout.tsx`'s `KEY_FACT_GRID_CLASSES`). |
| `PublicNavDrawer.tsx` | **New, Phase 3C.** Mobile navigation drawer — a real redesign of the mega menu for small screens, not a shrunk desktop layout. Each mega-menu item becomes a single-level disclosure accordion (same expand/collapse idiom as `FAQAccordion.tsx`) flattening its columns into one list; visual language matches `BankingSidebar.tsx`'s existing mobile drawer. |
| `BankingHeader.tsx` | Authenticated app header: mobile sidebar-menu button, `Logo`, truncated customer ID, log out. Unchanged in Phase 3C — out of scope (authenticated-app-only). |
| `BankingSidebar.tsx` | Persistent desktop sidebar (`lg:block`) and a mobile slide-in drawer (backdrop + transform), same nav item list, driven by `mobileOpen`/`onClose` props owned by `AppLayout`. Unchanged in Phase 3C. |
| `MobileNavigation.tsx` | Fixed bottom tab bar (mobile only) — Home/Accounts/History/Payments + a "More" tab that opens `BankingSidebar`'s drawer. Unchanged in Phase 3C. |
| `Footer.tsx` | **Restructured in Phase 3C**: 3 columns (Products/Support/Legal) → 4 (Products/Services/Support/Company), per the brief's requested footer structure. Product/Service/Support links now point at real public pages (`/savings-account`, `/wealth`, `/help`, homepage anchors) instead of protected routes a logged-out visitor can't reach — see [routing.md](routing.md)'s "Closing the public-page gap." Still: brand blurb, social placeholders (non-interactive, titled — not fake links), fictional-platform disclaimer. |

## `components/banking/` — domain components

| Component | Purpose |
|---|---|
| `BalanceCard.tsx` | A labeled money amount with an icon; `tone="primary"` renders the elevated gradient "hero" treatment (Dashboard's Total Balance), `tone="default"` a plain card (Savings/Current). |
| `AccountCard.tsx` | Accounts-page tile: type, masked account number with a show/hide toggle, status badge, balance, links to account details and pre-filtered transactions. |
| `TransactionRow.tsx` | One `<tr>`: reference, date, type, description, status badge, signed/colored amount. **Phase 8**: type/sign/color now come from `utils/transactionDirection.ts` rather than a static per-`transactionType` map — DEPOSIT/WITHDRAWAL are still unambiguous, but TRANSFER's direction (`TRANSFER IN`/`TRANSFER OUT` vs a plain, unsigned `TRANSFER`) is inferred from account-service's default per-leg description text, with an honest neutral fallback when the sender supplied a custom description (both ledger legs then carry identical text — see the Phase 8 journal entry's "backend gaps discovered" section). |
| `BeneficiaryCard` *(inline in `Beneficiaries.tsx`, Phase 8)* | Not extracted to its own file — a single page-local card per beneficiary (icon, name/nickname, bank, masked account number + IFSC, status badge, Edit/Remove actions) since it has no other caller yet. |
| `TransactionTable.tsx` | Wraps `TransactionRow`s in a `.table-scroll` container with a header row; handles its own loading (skeleton rows) and empty (`EmptyState`) states so callers don't reimplement either. |
| `QuickAction.tsx` | Dashboard quick-action tile — either a working `Link` or a disabled-looking button with a "Coming soon" caption, per the `comingSoon` prop. Never silently does nothing without saying so. |
| `ServiceCard.tsx` | Homepage product card: icon image, title, description, "Learn more" link. **Phase 3C**: added an optional `benefit` line (shown above the CTA, used by the redesigned "Popular Products" section) and a stronger hover (`-translate-y-1` + `shadow-elevation-3`) — both additive, no existing caller broke. |
| `SecurityBanner.tsx` | The 4-point security messaging grid used on the homepage's security section (and reusable at `compact` size elsewhere, e.g. a future Login enhancement). |
| `CardProductCard.tsx` | Credit/debit card listing tile: renders the matching original card SVG (`assets/cards/`) by `card.theme`, name, tagline, annual fee (via `formatINR`), top 3 benefits, "Explore Card" link to `/cards/:slug`. **Phase 3C**: added a subtle hover lift/rotate/shadow transition, no prop changes. |
| `LoanProductCard.tsx` | **New, Phase 3C.** Homepage loan showcase tile — replaces the old bare `<Link>` block: per-loan illustration (`assets/illustrations/loans/*-lifestyle.svg`), rate/amount/tenure fact row, a benefit line, "Explore" CTA, and an explicit "Illustrative BankSphere demo rate" disclaimer on every card (not just once for the section). |
| `InsightCard.tsx` | **New, Phase 3C.** Homepage "Latest Insights" tile — modeled on `OfferCard.tsx`'s simplicity: category thumbnail, `Badge`, title, date/reading-time row, and a "Read article" CTA that opens a `Modal` ("full articles aren't published yet" — same pattern as `ProductDetailLayout`'s apply CTA) since there's no article backend. |
| `ProductDetailLayout.tsx` | Shared shell for product detail pages (4 cards + 2 loan/deposit families +, as of Phase 3C, 1 account family use it via `CardDetail`/`LoanDetail`/`DepositDetail`/`SavingsAccountDetail`): hero, key-facts row, benefits grid, an optional `extra` slot (used for the embedded calculator on loan/deposit pages), eligibility/documents, FAQ, and a final CTA — both CTAs open the `Modal` Coming Soon dialog. |
| `ProductComparison.tsx` | Feature-comparison table (desktop/tablet) that renders the identical data as stacked per-product cards on mobile — both renderings share one data source, toggled with responsive display classes rather than JS breakpoint detection. Currently used on `/cards`. |
| `LoanCalculator.tsx` | Client-side EMI calculator (reducing-balance formula) — amount/rate/tenure in, EMI/principal/interest/total payable out, live-updating via `useMemo`. Used on `/loans` (general), each `/loans/:slug` (pre-seeded with that loan's starting rate), and, as of Phase 3C, the homepage's Loans section (pre-seeded with Home Loan's rate). |
| `FixedDepositCalculator.tsx` | Client-side FD maturity calculator (annual compounding) — amount/rate/tenure in, principal/interest/maturity out. Used on `/deposits`, each `/deposits/:slug`, and, as of Phase 3C, the homepage's Deposits section. |
| `PromotionBanner.tsx` | One promotional campaign: eyebrow/title/description/CTA beside an original promotional SVG (`assets/promotions/` or, as of Phase 3C, `assets/illustrations/digital-banking/mobile-banking-scene.svg`), with a `reverse` prop to alternate image/text sides. Homepage campaign copy stays in `Home.tsx`'s local `PROMOTIONS` array (homepage-specific, not reusable catalog data, so it isn't in `data/`). |
| `OfferCard.tsx` | A single demo offer (`data/offers.ts`) — category badge, title, description, and an explicit "BankSphere demo offer" disclaimer on every card. |
| `SecurityBanner.tsx` | The security/trust-principle messaging grid, `points` overridable via prop (`compact` size available too). **Phase 3C**: the homepage's consolidated "Why Choose BankSphere" section passes a new 4-principle `points` array (Secure by Design / 24/7 Digital Access / Transparent Banking / Customer First) instead of the component's technical-security default. |

## `components/forms/`

| Component | Purpose |
|---|---|
| `FormField.tsx` | Label/hint/error chrome as a render-prop wrapper, for custom controls that aren't `Input`/`Select` (currently only `AmountInput`). |
| `AmountInput.tsx` | Currency-prefixed amount field with a client-side decimal-pattern filter; the value stays a string until the caller (currently `AccountDetails`) parses and sends it as a `number` to `accountService.deposit()`/`withdraw()` — the backend's `@DecimalMin`/`@Digits` validation remains the authority, this is just input hygiene. |
| `FileUpload.tsx` | **New, Phase 9C.** This codebase's first file-upload control — no drag-and-drop zone, a hidden native `<input type="file">` triggered by a styled `Button`, built to the same `label`/`hint`/`error` contract as `Input`/`Select`/`FormField`. Used by `pages/kyc/Kyc.tsx` for document upload; validation (type/size) is client-side hygiene only, kyc-service's own `validateFile` remains authoritative. |

## `hooks/`

| Hook | Purpose |
|---|---|
| `useAsync.ts` | Shared fetch/loading/error/cancelled-on-unmount pattern (see [architecture.md](architecture.md)). |
| `useCustomer.ts` / `useAccounts.ts` / `useTransactions.ts` / `useBeneficiaries.ts` | Thin wrappers over `useAsync` for each service's data. `useBeneficiaries.ts` is new in Phase 8 — no `customerId` argument (the backend derives ownership from the JWT), just an `enabled` boolean. |
| `useKycApplication.ts` | **New, Phase 9C.** Deliberately not a thin `useAsync` wrapper: a `404` from `GET /api/v1/kyc/applications/me` means "never started KYC" (a normal state), not a fetch error — the hook catches that specific case and returns `{application: null, error: null}` rather than surfacing it as `error`. |
| `useInViewport.ts` | **New, Phase 3C.** `IntersectionObserver`-based, latches `isVisible` true on first intersect (no repeating animation). Checks `prefers-reduced-motion` *inside* the hook (not left to a CSS media query alone) and returns `isVisible: true` immediately with no observer in that case — see the hook's own doc comment for why gating only via CSS would risk content staying invisible for a reduced-motion user. Drives the homepage's per-section fade/slide-in (`Home.tsx`'s local `Reveal` wrapper). |

## `components/charts/` — dashboard data visualization

_New, Phase 3D._ Every chart is built from this customer's own real account/transaction data — never illustrative/placeholder figures — and follows a design-system-agnostic dataviz methodology (form → color → validate → marks → interaction → accessibility). Zero new dependencies: hand-rolled SVG, matching this project's existing `Icon`/`Modal`/`Toast` pattern.

| Component | Purpose |
|---|---|
| `ChartCard.tsx` | Shared chrome for every chart: title, optional legend, and a table-view toggle (the WCAG-clean accessibility twin every chart needs — every value a chart shows is also reachable as a plain `<table>`, not gated behind hover). |
| `LegendSwatch.tsx` | One legend entry — a small rect swatch (never a colored label; text stays in ink tokens, identity comes from the swatch) + label. |
| `LineChart.tsx` | Single-series balance trend. One hue (sequential job), so no legend box — the card title already says what's plotted. Crosshair + snap-to-nearest tooltip on hover/focus, direct end-label, hairline recessive gridlines. |
| `BarChart.tsx` | Grouped bar — Deposits vs Withdrawals per day. Two categorical series, so both a legend and a per-bar hover/focus tooltip ship; value labels ride each bar's tip (required relief — see `chartColors.ts`). |
| `PieChart.tsx` | Part-to-whole — balance split across a customer's own accounts, scoped to one currency (see `Dashboard.tsx`'s `accountBalanceSlices` — never sums across currencies). **Deliberately renders nothing below 3 segments**: a 1-slice pie is just the total and a 2-slice pie is a documented anti-pattern (a bar, or the two numbers, reads better) — `Dashboard.tsx` only mounts the `ChartCard` around it when the gate passes, and the existing balance cards already cover the 1–2 account case. |
| `chartColors.ts` | The chart color roles, mapped onto BankSphere's own brand tokens (not a generic reference palette) and validated as a categorical set with the dataviz skill's `validate_palette.js` — worst adjacent CVD ΔE 13.2, normal-vision ΔE 21.2, both clear of the 8/15 thresholds. `seriesGold` sits below 3:1 contrast on white by design; every chart using it ships a visible direct label rather than relying on the fill alone. |

**Where the data comes from** (`pages/dashboard/Dashboard.tsx`): the backend only exposes a *current* balance, not a balance-over-time series, so `balanceTrend` reconstructs history by walking the (already-fetched, newest-first) transaction list backward from today's balance, undoing each deposit/withdrawal — every point is a real, derived value, nothing interpolated. `depositsVsWithdrawals` sums real transaction amounts per calendar day. See the transform functions' own doc comments in `Dashboard.tsx` for the exact reasoning.

## `components/chat/` — support chatbot

See [docs/chatbot/architecture.md](../chatbot/architecture.md) for the full design; summary here:

| Component | Purpose |
|---|---|
| `ChatbotWidget.tsx` | Owns all chat state; floating button when closed, `ChatWindow` when open. Mounted once, globally, in `App.tsx` (not per-page). |
| `ChatWindow.tsx` | The chat panel: header, scrollable message list with auto-scroll, quick questions, input bar, Escape-to-close. |
| `ChatMessage.tsx` | One message bubble, styled by `role` (`user`/`assistant`). |
| `ChatInput.tsx` | Controlled text field + send button. |
| `QuickQuestions.tsx` | The six suggested-question chips. |
| `TypingIndicator.tsx` | Three-dot "assistant is typing" animation shown while a response is pending. |

All six talk only to `services/chatService.ts` — a mock, keyword-matched response generator with no network calls (see the chatbot docs for why, and the security boundary it must respect even as a mock).

## `services/` — Phase 8 additions

| File | Purpose |
|---|---|
| `beneficiaryService.ts` | **New.** CRUD wrapper over a new `beneficiaryApiClient` (`VITE_BENEFICIARY_SERVICE_URL`, default `http://localhost:8084`) — `getBeneficiaries`/`getBeneficiary`/`createBeneficiary`/`updateBeneficiary`/`deactivateBeneficiary`, same one-object-of-async-functions shape as `accountService.ts`/`transactionService.ts`. |
| `accountService.ts` | Added `transfer(request)` → `POST /api/v1/accounts/transfer`, alongside the existing `deposit`/`withdraw`. Still the same client (`accountApiClient`) — transfer lives in account-service, not a separate service. **Phase 8B:** `transfer()`'s request shape changed to `destinationAccountNumber`/`destinationIfsc` (no more `destinationAccountId`); added `resolveRecipient(request)` → `POST /api/v1/accounts/resolve-recipient` for the pre-transfer "verify payee" step — see [ADR-005](../architecture/decisions/ADR-005-recipient-resolution.md). |
| `apiClient.ts` | The response interceptor now throws `utils/apiError.ts`'s `ApiError` (still a plain `Error` subclass — `err.message` behavior is unchanged for every existing caller) instead of a bare `Error`, additionally carrying `status` and `details` from the backend's `ErrorResponse`. `utils/apiError.ts#getFriendlyErrorMessage` maps status codes to the consistent copy Step 13 of the Phase 8 task asked for (401/403/404/409/422/500/network), with per-call overrides for operation-specific wording (e.g. a 422 means "insufficient balance" on a transfer but "account not active" on a deposit). **Phase 9C:** added `kycApiClient` (`VITE_KYC_SERVICE_URL`, default `http://localhost:8086`). **Phase 9D:** `customerApiClient` now sets `withCredentials: true` (the only client that does — needed for the refresh-token cookie, since customer-service is the only service whose CORS config allows credentials); every client gained a silent-refresh-on-401 interceptor that calls `/token/refresh` at most once per failing request, sharing one in-flight promise across concurrent 401s so N simultaneous expired-token requests trigger exactly one rotation. |
| `kycService.ts` | **New, Phase 9C.** `getMyApplication`/`createApplication`/`uploadDocument` (multipart, `FormData` + `documentType` query param)/`submit`/`resubmit` — same one-object-of-async-functions shape as every other service file. |
| `stepUpService.ts` | **New, Phase 9D.** `requestTransferStepUp`/`verifyStepUp`, both through `customerApiClient` (step-up is a customer-service concern, not account-service, even though it protects an account-service operation — see [ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md)). |
| `authService.ts` | **Phase 9D additions:** `requestOtp`/`verifyOtp` (login OTP), `getDevOtpInbox` (local development only). Existing `register`/`login`/`logout`/`getCurrentCustomer` unchanged. |

## Testing (new in Phase 8)

No test framework existed before this phase (`grep`-confirmed zero `*.test.*` files and no `vitest`/`jest`/`@testing-library/*` dependency). Added as the minimum necessary infrastructure to fulfil the task's explicit test requirements — Vitest + `@testing-library/react` + `@testing-library/jest-dom` + `@testing-library/user-event` + `jsdom`, configured via `vite.config.ts`'s `test` block (no separate config file) and `src/test/setup.ts` (jest-dom matchers + RTL's per-test `cleanup()`, registered explicitly since Vitest `globals` mode is deliberately off — test files import `describe`/`it`/`expect`/`vi` explicitly). Run via `npm test` (`vitest run`). Tests are colocated next to the file they cover (`Foo.tsx` → `Foo.test.tsx`), following no prior convention since none existed. Fixing `setup.ts` to actually reset mocks between tests (`vi.resetAllMocks()` in a global `afterEach`) and to run RTL's `cleanup()` were both necessary — their absence caused real cross-test pollution (accumulated DOM nodes, leaked call counts) during this phase, not just theoretical risk.

## Removed in Phase 2A/2B (for reference)

`components/Card.tsx` and `components/StatusBanner.tsx` (the original Phase 1 components) were deleted, superseded by `components/common/Card.tsx` and `components/common/ErrorState.tsx`/`Spinner.tsx`/`Skeleton.tsx` respectively — kept as one implementation each rather than leaving the old ones alongside enhanced new ones. Every usage was updated to the new import paths in that same phase; nothing in this phase reintroduced the old pattern.
