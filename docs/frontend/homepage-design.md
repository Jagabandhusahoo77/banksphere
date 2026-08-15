# Homepage & Public Site Redesign (Phase 3C)

_Status: Phase 3C. Covers the public marketing site redesign — the homepage's 13-section structure, the mega-menu navigation, the animation approach, and what was deliberately not built. See [components.md](components.md), [routing.md](routing.md), and [product-catalog.md](product-catalog.md) for the component/route/data-level detail this document ties together._

## Why this phase happened

Through Phase 1/2, BankSphere's public site was functionally complete but read as a "developer banking demo" rather than a premium, trustworthy financial institution's website — a flat homepage, a header with non-interactive Personal/Business/NRI tabs, and a handful of abstract SVG illustrations. This phase redesigns the public homepage and navigation into something closer to a major bank's information architecture and visual polish, while keeping BankSphere's branding entirely original (see [design-system.md](design-system.md) and the fictional-project rule in the repo root `CLAUDE.md`) and never fabricating functionality the backend doesn't have.

## No reference screenshot was actually available

The redesign brief referenced a screenshot for visual direction that never actually reached this implementation — a shared link to it could not be opened (only a generic app shell rendered, not the underlying content). This document and the implementation it describes were built from the brief's detailed written description alone, not from a copied layout. Nothing here is a pixel-for-pixel reproduction of any real bank's site.

## Homepage structure — 13 sections

| # | Section | What changed |
|---|---|---|
| 1 | Hero | Rewritten copy ("Banking that fits your life goals."), a new people-based illustration (`hero-lifestyle.svg`, see Imagery below), trust strip re-labeled (Secure Banking / 24/7 Digital Access / Trusted Service) — no invented statistics. |
| 2 | Quick Banking Categories | New. Horizontal row (scrollable on mobile) of 7 categories (Accounts/Cards/Loans/Investments/Insurance/Payments/Offers), driven by `data/navigation.ts`'s `QUICK_CATEGORIES`. |
| 3 | Popular Products | Narrowed to the brief's 5 flagship items (Savings/Credit Cards/Home Loan/Personal Loan/Fixed Deposit), each with a standout benefit line and a stronger hover treatment. |
| 4 | Card showcase | Same 3 featured `CardProductCard`s, new hover (lift/rotate/shadow). |
| 5 | Mobile/Digital Banking Promotion | New. A `PromotionBanner` instance with a new illustration, explicitly not claiming a real downloadable app exists (none does). |
| 6 | Loans | Componentized into `LoanProductCard` (rate/amount/tenure/benefit/illustration/disclaimer on every card), plus an inline `LoanCalculator` pre-seeded with Home Loan's rate. |
| 7 | Fixed Deposit / Savings | Same rates (unchanged: FD 7.25%, RD 6.75% — confirmed against `data/deposits.ts`, nothing invented), plus an inline `FixedDepositCalculator`, elevated card treatment. |
| 8 | Exclusive Offers | Same `OFFERS` grid, repositioned, with the four `PROMOTIONS` campaigns folded in as a secondary row (no separate slot for both in the brief's structure). |
| 9 | Why Choose BankSphere / Trust | Consolidates the old separate "Security" band and "Why BankSphere?" grid into one dark section using 4 principles (Secure by Design / 24/7 Digital Access / Transparent Banking / Customer First) — principles, not invented statistics. |
| 10 | Digital Banking Services | New. 6 items (UPI/Transfer/Bill Payments/Statements/Service Requests/Card Controls), reusing existing `assets/icons/*.svg`, each routed honestly (real protected page or, where nothing exists, a `ComingSoonPage`). |
| 11 | Financial Goals / Lifestyle banner | New. A family-scene illustration, "Your goals. Our priority." |
| 12 | Latest Insights | New. 5 static demo editorial cards (`data/insights.ts` + `InsightCard`), explicitly not real published articles. |
| 13 | FAQ | Unchanged content, moved to the very end. |

Removed as a standalone section: the old "Support/chatbot teaser" — redundant once the chatbot is understood as a persistent global element already visible on every page including this one; its CTA moved to the new `/help` page.

## Mega-menu navigation

`PublicHeader.tsx` was rewritten around a single data source, `data/navigation.ts`'s `PRIMARY_NAV`, consumed by three components so a link target is only ever defined once:

- **`MegaMenuPanel.tsx`** (desktop) — a full-width dropdown positioned under the header, opens on hover or click, closes on `Escape`, outside click, or route change.
- **`PublicNavDrawer.tsx`** (mobile) — a real redesign for small screens, not a shrunk desktop layout: each mega-menu item becomes a single-level disclosure accordion (same interaction idiom as `FAQAccordion.tsx`) flattening its columns into one scrollable list.
- **`Footer.tsx`** — its Products/Services columns draw from the same link targets where applicable.

**Personal**'s mega menu links to BankSphere's real product catalogs (Cards/Loans/Deposits already existed; Savings Account is new this phase — see below). **Business**, **NRI**, and **Premium Banking** are real top-level navigation items (replacing the old non-interactive segment tabs), but every item inside those three menus routes to a single honest `ComingSoonPage` per segment — BankSphere has no real business/NRI/premium-banking products, and fabricating entire fictional product catalogs for them (rates, eligibility, documents, multiple pages each) was explicitly scoped out of this phase.

## Closing a gap the redesign would have made worse

Before this phase, `/accounts`, `/investments`, `/payments`, and `/support` only existed as **protected** (login-required) pages — there was no public page describing these to an anonymous visitor. The new Personal mega-menu and Quick Categories row would otherwise have linked straight into a login wall for a *product* page, which reads as broken (unlike a login wall for an *action* — Transfer, Payments, Service Requests — which is normal, expected bank-website behavior). This phase added:

- `/savings-account` — a **real** page (not a stub), reusing `ProductDetailLayout` and a new minimal `data/accounts.ts` (deliberately one product — Savings; Current/Salary accounts have no illustrative data yet).
- `/help` — a **real** page recomposing the already-real `FAQAccordion`/`FAQS` content, rather than a stub, since a brand-new "Help & Support" primary-nav item landing on a login wall would be a poor first impression.
- `/account-types`, `/wealth`, `/insurance` — honest `ComingSoonPage`s, since Current/Salary accounts, Investments, and Insurance have no backend product behind them.

Full detail and the exact route table: [routing.md](routing.md).

## Imagery: real photography (photography correction pass)

The initial Phase 3C pass used original SVG illustration for imagery-heavy sections (see "SVG-only history" below) — this project had no image-generation tool and no licensed stock-photo source available to it. The user separately generated 12 photographs (one per concept: Hero, Savings/FD, Home/Car/Education/Personal Loan, Credit Cards, Mobile Banking, Investments, Travel/Rewards, Digital Banking, Financial Goals) and supplied them as local files, which were then converted to WebP (Pillow, quality 80, resized to their display width — 25.4MB combined source down to 0.93MB) and integrated in place of the corresponding illustrations.

**Two of the twelve photographs had baked-in marketing text that conflicts with this project's own no-invented-statistics rule** — "Trusted by Millions" (Mobile Banking) and "Choose from 1000+ hotels worldwide" (Travel) are both fabricated claims, and since the source is a flattened photograph there's no way to edit the pixels out. Both live in a bottom marketing bar in their respective source images, so both are cropped out of view via a Tailwind `object-position` bias (`object-right-top`) rather than shown — a real constraint worth being explicit about, not a silent fix. Several other photos have a baked-in text/logo overlay panel (an artifact of being generated as complete ad-style creatives rather than raw lifestyle photography) that would otherwise visually duplicate the page's own React-rendered headline/copy right next to it; those are similarly cropped toward the photographic subject via `object-position` (`object-right` for panels on the left, `object-bottom` for panels on top) instead of shown in full. None of this cropping was visually verified in a browser — see Verification below.

The pre-existing "editorial illustration" SVGs (`hero-lifestyle.svg`, `goals-family.svg`, `mobile-banking-scene.svg`, `accounts-overview.svg`, the four `*-lifestyle.svg` loan illustrations) are kept on disk, unused by the sections they originally served, per explicit instruction not to delete them — they may still suit smaller/decorative contexts.

### SVG-only history

The original Phase 3C pass extended BankSphere's existing 100%-hand-authored-SVG illustration convention with a warmer, people-based "editorial illustration" style (simplified flat-geometric human figures — circle heads, rounded-shape torsos, dot eyes, no realistic faces) rather than using photography, since no photography was available at the time. That reasoning and those assets are preserved for the record above; they were superseded once real photography became available, not proven wrong.

## Animation: CSS-only, no new dependency

No animation library (framer-motion or similar) was added — consistent with this project's pattern of staying dependency-light (hand-rolled `Icon`/`Modal`/`Toast` instead of pulling in packages, per the repo root `CLAUDE.md`). Instead:

- **Hover states** — Tailwind transition utilities (`transition-all`, `hover:-translate-y-1`, `hover:shadow-elevation-3`, etc.) on cards, buttons, and nav items.
- **Scroll-reveal** — one small custom hook, `hooks/useInViewport.ts`, wraps each homepage section in a one-time fade/slide-in on first scroll into view (never repeats). It checks `prefers-reduced-motion` *inside* the hook, not via a CSS media query alone — a reduced-motion user gets `isVisible: true` immediately with no observer ever created, so content can never get stuck at `opacity-0`.
- **Mega-menu transitions** — a short (~150ms) close-delay timer so moving the pointer from a nav trigger down into the panel doesn't flicker-close it.

## Background treatment (Phase 3D)

The homepage's section backgrounds were flat/plain outside the hero. Phase 3D added a curated, alternating rhythm of soft gradients (`bg-gradient-to-*` between existing brand tokens — `brand-primary-light`, `brand-accent-light`, `brand-secondary/10`, `surface-muted`, white — never a raw Tailwind palette color or an arbitrary value) plus, in the hero and the dark "Why Choose BankSphere" section, two absolutely-positioned, heavily blurred (`blur-3xl`) low-opacity brand-colored circles for a soft glow. Deliberately not every section: alternating colorful/plain sections reads as intentional; a gradient on every single section would be the "visually noisy background" this project's own design-system guidance already warns against. Quick Categories, Mobile Promo, Digital Banking Services, and Insights stay plain white/unadorned — they either already carry visual weight from a photo or work best as a breathing-room section between two decorated neighbors.

## What was deliberately not done

- **No Trust/Partner-logo section** — BankSphere has no real partners or certifications to name; a section implying otherwise would misrepresent a fictional platform as having real institutional backing.
- **No invented statistics anywhere** — no customer counts, branch counts, awards, or "X million users" claims, per the brief's own instruction and this project's existing rate-disclaimer discipline.
- **No app-store implication** — the Mobile Banking Promotion section never claims a real downloadable app exists, because none does.
- **No full Business/NRI/Premium Banking product catalogs** — see Mega-menu navigation above.
- **No mobile sticky CTA bar** — not required by the brief's explicit section list; the floating chatbot already occupies the bottom-right corner on mobile, and adding a second persistent floating element wasn't judged worth the added complexity/z-index coordination for this pass.
- **No real article backend for Latest Insights** — `InsightCard`'s "Read article" CTA opens a Modal explaining the full article isn't published yet, the same honest pattern used by every "Apply"/"Open" CTA elsewhere in this app.

## Verification performed

No browser was available in this environment (same constraint recorded in every prior frontend phase — see the Phase 3C engineering journal entry). What was actually done: `tsc -b && vite build` clean after every implementation stage; a temporary local `vite` dev server with `curl` HTTP-status checks confirming every new and existing public route resolves (200/SPA-shell); a grep-based audit for unused imports, non-token colors, missing `alt`/`aria-label`, dangling links, and hotlinked external image URLs. Actual rendered/interactive UI, real mega-menu hover/keyboard behavior, real animation timing, and the actual visual framing/crop of every `object-position`-adjusted photograph were never visually observed — the crop choices for the photography correction pass are a best-effort read of each source image's composition, not a browser-verified result.
