# 2026-08-12 — Phase 3C: Public Website Redesign

## Phase objective

Redesign BankSphere's public marketing site — primarily the homepage and header navigation — from a "functional developer banking demo" into a premium, trustworthy, modern digital banking platform feel, comparable to a major bank's site in structure and polish while remaining entirely original to BankSphere (no ICICI Bank or other real institution's branding, photography, or claims). The brief was extremely detailed (32 sections) but explicit that existing architecture, backend APIs, and functionality must not break, and that no new frontend frameworks or unnecessary dependencies should be introduced.

This is referred to as "Phase 3C" in this project's own numbering (not the original 14-phase roadmap's numbering) to avoid colliding with "Phase 3A" (authentication, same day) and the reserved "Phase 3B" (money transfer, not started) — see [roadmap.md](../00-project-overview/roadmap.md)'s naming note.

## No reference screenshot was actually available

The task referenced a shared ChatGPT conversation containing a reference screenshot for visual direction. Attempting to open it only returned the ChatGPT app's login-gated shell, not the underlying conversation or image — confirmed by fetching the link directly. The user then pasted the full 32-section written brief directly into the conversation, which is what this phase was actually built from. No image was ever available at any point in this phase; nothing here is a reproduction of any screenshot, real or otherwise.

## Scope decisions made before implementation

Three open questions were raised with the user before writing any code, since guessing wrong on any of them would have meant redoing significant work:

1. **Imagery** — the brief asked heavily for realistic lifestyle photography. BankSphere's existing design system is 100% hand-authored SVG illustration (documented as deliberate in [design-system.md](../frontend/design-system.md)), and this environment has no licensed stock-photo source and cannot legally hotlink external images. **Decision: extend the existing illustration convention with a warmer, people-based "editorial illustration" style** — simplified flat-geometric human figures, still fully original SVG, zero licensing risk.
2. **Animation** — no animation library exists in this project. **Decision: CSS-only (Tailwind transitions) plus one small custom `useInViewport` hook**, no framer-motion or similar, consistent with this project's established dependency-light pattern.
3. **Business/NRI mega-menu scope** — building full fictional product catalogs for Business/NRI/Premium Banking (rates, eligibility, documents, many pages) would be a project on the scale of the homepage redesign itself, twice over. **Decision: build the mega-menu navigation now; every Business/NRI/Premium Banking item routes to one honest `ComingSoonPage` per segment**, not fabricated product pages.

## A gap discovered during exploration, not anticipated by the brief

Before writing any code, the existing frontend was inspected end to end (routes, components, design system, assets, product-catalog pattern, `ComingSoonPage` usage, chatbot positioning) via a dedicated research pass. This surfaced a real problem the brief hadn't accounted for: `/accounts`, `/investments`, `/payments`, and `/support` only existed as **protected** (login-required) pages. The brief's own requested Personal mega-menu and homepage "Quick Banking Categories" row would otherwise have linked an anonymous visitor straight into a login wall for a *product* page — a materially worse first impression than the demo already had, and arguably worse than not building the new navigation at all. A login wall for an *action* (Transfer, Payments, Service Requests) is normal and expected; a login wall for a *product page* is not.

This was raised with the user as a fourth decision point (full fix — 8 new public pages — vs. a minimal 3-page fix). **User chose the full fix.**

## Work completed

**Icons** (`components/common/Icon.tsx`): 8 new hand-authored stroke icons (`calendar`, `tag`, `umbrella`, `percent`, `car`, `graduation-cap`, `globe`, `star`) — same file, same style, no new dependency.

**Data** (`frontend/src/data/`): three new files.
- `navigation.ts` — `PRIMARY_NAV` (the mega-menu structure) and `QUICK_CATEGORIES` (the homepage category row), a single source of truth consumed by three different components instead of each hardcoding the same link targets independently.
- `insights.ts` — 5 static demo editorial entries for the new "Latest Insights" homepage section, explainer-style titles, no real-publication attribution.
- `accounts.ts` — a single Savings Account product, matching `cards.ts`/`loans.ts`/`deposits.ts`'s field shape exactly so `ProductDetailLayout` needed zero changes to render it.

**Assets** (`frontend/src/assets/illustrations/`): 13 new original SVGs across 4 new subfolders (`hero/`, `lifestyle/`, `digital-banking/`, `insights/`) plus 5 more into existing subfolders (`loans/`, `accounts/`), extending rather than duplicating the existing illustration directory convention. The retired `assets/images/hero-banking.svg` (superseded by the new hero illustration, confirmed as its only consumer via grep) was deleted rather than left orphaned.

**Components**: `LoanProductCard.tsx` and `InsightCard.tsx` (new, `components/banking/`); `ServiceCard.tsx` (extended with an optional `benefit` prop and a stronger hover, backward compatible) and `CardProductCard.tsx` (hover-only change) both updated in place; `hooks/useInViewport.ts` (new) for the homepage's one-time scroll-reveal.

**Routing**: 8 new public routes added to `App.tsx` (additive only — no existing route path, element, or nesting changed): `/savings-account` and `/help` are real pages; `/business`, `/nri`, `/premium-banking`, `/account-types`, `/wealth`, `/insurance` are honest `ComingSoonPage`s (the first use of that component on the public site — previously authenticated-app-only).

**Navigation**: `MegaMenuPanel.tsx` (new, desktop dropdown) and `PublicNavDrawer.tsx` (new, mobile drawer — a real redesign for small screens using the same disclosure-accordion idiom as `FAQAccordion.tsx`, not a shrunk desktop layout) both consume `data/navigation.ts`. `PublicHeader.tsx` rewritten to own open-menu/drawer state, hover/click/Escape/outside-click/route-change handling.

**Footer**: restructured from 3 columns (Products/Support/Legal) to 4 (Products/Services/Support/Company) per the brief, pointing at the new real public pages instead of protected routes.

**Homepage** (`Home.tsx`): rewritten from 11 sections to 13, described in full in [docs/frontend/homepage-design.md](../frontend/homepage-design.md) (new doc). Old "Support/chatbot teaser" section removed as redundant (the chatbot is already a persistent global element on every page); its CTA moved to the new `/help` page.

## Problems encountered and fixes

**1. `<Link>` to a homepage in-page anchor doesn't scroll.** Several CTAs (Quick Categories' Payments/Offers tiles, the Mobile Banking promotion's CTA, Footer's Services links) needed to point at in-page anchors on the homepage (e.g. `/#digital-banking-services`). React Router's `<Link>` does client-side navigation via `history.pushState`, which does **not** trigger the browser's native same-document hash-scroll behavior a real `<a>` click would. **Fix:** added a `useEffect` in `Home.tsx` watching `location.hash` that manually calls `scrollIntoView({ behavior: "smooth" })` on the target element — covers both "already on `/`" and "navigated here from another page" cases uniformly, with no change needed to `PromotionBanner`/`Footer`'s existing `<Link>`-based CTAs. Target sections got `scroll-mt-20` so the sticky header doesn't overlap the scrolled-to content.

**2. Two `position` values on one element.** An early draft of the rewritten `PublicHeader.tsx` added a `relative` Tailwind class alongside the existing `sticky` class, reasoning (incorrectly, at first) that the mega-menu panel's `absolute` positioning needed an explicit `relative` ancestor. `position: sticky` already establishes a containing block for `position: absolute` descendants, the same as `relative` would — the two classes together would have set two different `position` values on the same element, with the actual result depending on Tailwind's generated CSS ordering rather than anything intentional. Caught by re-reading the diff before running the build, not by a build failure (Tailwind would have compiled this without error either way) — fixed by removing the redundant `relative` class and documenting why in the component's header comment.

**3. Anchor target lost when a section was consolidated.** The old separate "Security" (`id="security"`) section was merged into the new "Why Choose BankSphere" section during the 13-section rewrite. `Footer.tsx`'s pre-existing "Security" link (`/#security`) would have silently pointed at nothing once the old section's `id` disappeared. Caught by grepping for `#security` usage across the codebase before finalizing the rewrite, rather than after — the `id` was carried over onto the new consolidated section.

## Tests / verification performed

- `tsc -b` run after every implementation stage (icons → data → assets → components → routes → header/mega-menu → footer → homepage) — clean at every checkpoint, not just at the end.
- `npm run build` (`tsc -b && vite build`) — clean, final bundle ~406 KB JS / ~31 KB CSS (up from ~372 KB / ~28 KB pre-redesign; +13 new SVGs, +2 nav components, +2 banking components, +8 pages, +3 data files).
- A temporary local `vite` dev server, `curl`-checked against every new route (`/business`, `/nri`, `/premium-banking`, `/account-types`, `/wealth`, `/insurance`, `/savings-account`, `/help`) and every pre-existing public route (`/`, `/about`, `/contact`, `/cards[/:slug]`, `/loans[/:slug]`, `/deposits[/:slug]`, `/login`, `/register`) — all returned `200`. Server stopped immediately after.
- A grep-based audit: no duplicate route paths in `App.tsx`; `hero-banking.svg` confirmed unreferenced before deletion; `#security` confirmed to have exactly one consumer before relocating its `id`.

**Not performed, and not claimed:** real browser rendering, real mega-menu hover/keyboard interaction, real scroll-reveal animation timing, or any visual/accessibility QA beyond static analysis — no browser is available in this environment, the same constraint recorded in every prior frontend phase (Phase 1, Phase 2A/2B, Phase 2 banking experience, Phase 3A).

## Known limitations (deliberate, documented — not oversights)

Recorded in full in [docs/frontend/homepage-design.md](../frontend/homepage-design.md)'s "What was deliberately not done" section: no Trust/Partner-logo section (no real partners exist to name), no invented statistics anywhere, no app-store implication for "Mobile Banking" (no real app exists), no full Business/NRI/Premium Banking product catalogs, no mobile sticky CTA bar (the chatbot already occupies that screen position), no real article backend for Latest Insights.

## What was NOT done

No backend code changed. No new dependencies added. No Kafka/Redis/Kubernetes/Terraform/CI-CD/real LLM/native app/real payment gateway. Authentication (`AuthContext`, `ProtectedRoute`, `apiClient`, JWT flow) and every protected route are untouched — this was a public-site-only pass, verified by not touching any file under `pages/{dashboard,accounts,transactions,transfer,cards,loans,payments,investments,profile,support}/`, `context/`, `routes/`, or `services/`.

## Next

Per the task's own instructions: do not start another development phase automatically. Reasonable next steps (not started): populate `docs/frontend/product-catalog.md`-style real Business/NRI product data if that phase is ever prioritized; Phase 3B (money transfer) remains the more natural next backend-touching phase.
