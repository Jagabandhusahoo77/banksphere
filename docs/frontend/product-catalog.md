# Product Catalog

_Status: Phase 3C — all content on this page is fictional BankSphere demo data for a portfolio project. None of it is a real interest rate, fee, or offer, and none of it is sourced from or represents ICICI Bank or any other real institution. Every place this data renders in the UI carries its own disclaimer text — this document is the single source for what that data actually is._

## Data architecture

All product/rate/offer content lives in `frontend/src/data/`, not scattered through JSX:

```text
frontend/src/data/
├── cards.ts        CARD_PRODUCTS + getCardBySlug()
├── loans.ts         LOAN_PRODUCTS + getLoanBySlug()
├── deposits.ts       DEPOSIT_PRODUCTS + getDepositBySlug()
├── accounts.ts        ACCOUNT_PRODUCTS + getAccountBySlug()   (new, Phase 3C)
├── offers.ts            OFFERS
├── faqs.ts                FAQS
├── insights.ts              INSIGHTS                          (new, Phase 3C — editorial content, see below)
└── navigation.ts               PRIMARY_NAV, QUICK_CATEGORIES  (new, Phase 3C — nav config, not product content, see note below)
```

Each product file exports a typed array plus (for cards/loans/deposits/accounts) a `getXBySlug()` lookup used by the dynamic detail-page routes (`/cards/:slug`, `/loans/:slug`, `/deposits/:slug`, and the fixed single-product `/savings-account`). Pages and components import from here — asset imports (SVG card designs, icons) are kept in the *components/pages* that render them, not in the data files themselves, so the data stays framework-agnostic and easy to eventually replace with a real backend response shape.

**`navigation.ts` is a deliberate exception to this file's "all `data/*.ts` is product content" pattern** — it's navigation/UI configuration (the mega-menu structure, the homepage quick-category row), not fictional financial product data. It was still placed under `data/` rather than a new top-level directory because its purpose is identical in spirit: a single source of truth an otherwise-scattered set of components would each hardcode independently. See [architecture.md](architecture.md) and [components.md](components.md).

## Card products (`data/cards.ts`)

| Card | Network | Annual fee | Theme |
|---|---|---|---|
| BankSphere Platinum Card | VISA | ₹1,499 (waived above ₹3,00,000 spend) | `platinum` |
| BankSphere Cashback Card | RuPay | ₹999 (waived year 1) | `cashback` |
| BankSphere Travel Card | VISA | ₹1,999 (waived above ₹4,00,000 spend) | `travel` |
| BankSphere Rewards Card | Mastercard | ₹1,299 (waived above ₹2,50,000 spend) | `rewards` |
| BankSphere Debit Card | RuPay | Free | `debit` |

Each `theme` maps to an original SVG card design in `frontend/src/assets/cards/banksphere-{theme}.svg` — see [design-system.md](design-system.md#card-designs) for how those were built. Card network names (VISA/Mastercard/RuPay) are rendered as **plain text**, not as a redrawn trademarked network logomark — see that same section for why.

Routes: `/cards` (catalog + comparison table), `/cards/:slug` (detail page with benefits/eligibility/documents/FAQ and an "Apply" CTA that opens a Coming Soon modal — see [routing.md](routing.md)).

## Loan products (`data/loans.ts`)

| Loan | Starting rate | Max amount | Max tenure |
|---|---|---|---|
| Home Loan | 8.50% p.a. | ₹5 Crore | 30 years |
| Personal Loan | 10.99% p.a. | ₹25 Lakh | 7 years |
| Car Loan | 9.25% p.a. | ₹50 Lakh | 7 years |
| Education Loan | 9.00% p.a. | ₹50 Lakh | Flexible |

**These are the exact illustrative rates specified for this phase — fictional, not real market or ICICI rates.** Every rendering carries "Illustrative BankSphere demo rate. Actual rates may vary." Routes: `/loans` (catalog + `LoanCalculator`), `/loans/:slug` (detail page, calculator pre-seeded with that loan's starting rate).

## Deposit products (`data/deposits.ts`)

| Deposit | Rate | Tenure | Minimum |
|---|---|---|---|
| Fixed Deposit | Up to 7.25% p.a. | 7 days – 10 years | ₹1,000 |
| Recurring Deposit | Up to 6.75% p.a. | 6 months – 10 years | ₹500/month |

Same fictional-rate disclaimer as loans. Routes: `/deposits` (catalog + `FixedDepositCalculator`), `/deposits/:slug`.

## Account products (`data/accounts.ts`, new in Phase 3C)

| Account | Rate | Minimum balance |
|---|---|---|
| Savings Account | Up to 4.00% p.a. | ₹0 |

Deliberately a single product — Current and Salary accounts don't have illustrative data behind them yet, so they route to an honest `ComingSoonPage` (`/account-types`) instead of being fabricated here. Same fictional-rate disclaimer as loans/deposits. Route: `/savings-account` (real detail page via `ProductDetailLayout`, same pattern as cards/loans/deposits — no catalog/list page since there's only one product).

## Offers (`data/offers.ts`)

Four fixed demo offers (10% off select merchants, 2X weekend rewards, a special digital-FD benefit, travel benefits on select cards), rendered via `OfferCard` on the homepage's "Exclusive Offers" section (renamed from "Latest BankSphere Offers" in Phase 3C; same data, same component). Each card explicitly states "BankSphere demo offer — for portfolio demonstration only."

## Insights (`data/insights.ts`, new in Phase 3C)

Five static demo editorial entries — one per category (Digital Banking, Personal Finance, Investments, Saving, Security) — rendered via `InsightCard` on the homepage's "Latest Updates & Insights" section. Explainer-style titles (e.g. "Understanding UPI: how instant payments work"), not breaking-news style, and not attributed to any real publication. **This is homepage-only editorial content, not a reusable product catalog** — same category as Promotions below, kept in `data/` for the typed-array convention but with no `getXBySlug()` lookup (there's no detail route; each card's "Read article" CTA opens a Modal explaining that full articles aren't published yet, since there's no article backend).

## FAQs (`data/faqs.ts`)

Seven Q&As covering account opening, savings/current accounts, what an FD is, the demo loan rates, contacting support, blocking a card, and viewing transactions — rendered via `FAQAccordion` on the homepage and on every product detail page (`ProductDetailLayout`).

## Calculators

Both are pure client-side React components, no backend call:

- **`LoanCalculator.tsx`** — standard reducing-balance EMI formula (`EMI = P·r·(1+r)ⁿ / ((1+r)ⁿ-1)`), inputs amount/rate/tenure, outputs EMI/principal/total interest/total payable.
- **`FixedDepositCalculator.tsx`** — annual-compounding maturity formula (`Maturity = P·(1+r/100)ᵗ`), inputs amount/rate/tenure, outputs principal/interest earned/maturity amount.

**Numeric handling:** both run on plain JavaScript `number`s (`IEEE754` doubles) — there is no `BigDecimal` equivalent available client-side without adding a new dependency, which this phase avoids. The mitigation actually applied: every intermediate calculation is rounded (`Math.round`) before it's ever displayed, and display always goes through `formatINR()` (`Intl.NumberFormat`), so a floating-point artifact like `12345.679999999` is never rendered — only a clean, rounded rupee amount. This is a real, documented limitation (see the engineering journal), not silently glossed over: these calculators produce *estimates* for a fictional product, explicitly labeled as such, not values feeding a real ledger (contrast with the actual backend, which uses `BigDecimal`/`NUMERIC` for every real balance — see [docs/00-project-overview/scope.md](../00-project-overview/scope.md)).

## Promotional campaigns (`components/banking/PromotionBanner.tsx`)

Four fictional campaigns defined inline in `Home.tsx`'s `PROMOTIONS` array (not a `data/` file — these are homepage-specific copy, not a reusable catalog like cards/loans/deposits): "Go digital, get rewarded" (account opening), "Make your next journey more rewarding" (travel/rewards cards), "Plan today, own tomorrow" (home loans), "Grow your savings" (deposits). Each pairs with an original SVG in `frontend/src/assets/promotions/`. **Phase 3C**: folded into the homepage's "Exclusive Offers" section as a secondary row beneath the `OfferCard` grid, rather than kept as a separate section — both are "offers/campaigns" content and the redesign's requested 12-section structure has no separate slot for both. A fifth `PromotionBanner` instance, with a new illustration (`assets/illustrations/digital-banking/mobile-banking-scene.svg`), also now backs the homepage's dedicated "Mobile/Digital Banking Promotion" section.

## Product comparison

`ProductComparison.tsx` renders a feature table on tablet/desktop and the same data as stacked per-product cards on mobile (both in the DOM, toggled by responsive display classes — see [design-system.md](design-system.md) for why, and [components.md](components.md) for the component itself). Currently used on `/cards` to compare the four credit cards' annual fee, network, and headline benefit.
