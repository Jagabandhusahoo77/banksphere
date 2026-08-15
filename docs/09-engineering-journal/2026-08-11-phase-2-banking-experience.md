# 2026-08-11 — Phase 2: Complete Banking Website Experience

## Objective

Take the existing BankSphere frontend (design system, component library, public/authenticated shells — all from the earlier Phase 2A/2B session the same day) and complete it into a full commercial-bank-style public website: card products with original visual designs, loan and deposit products with calculators, promotional campaigns, an offers section, a FAQ, and a mock customer-support chatbot — while explicitly not touching the Phase 1 backend, not implementing real authentication/payments/card/loan/investment processing, and not starting any infrastructure work.

## Product data architecture

All new product/rate/offer/FAQ content lives in `frontend/src/data/{cards,loans,deposits,offers,faqs}.ts` — typed arrays plus `getXBySlug()` lookups for the three dynamic detail routes, kept deliberately separate from asset imports (SVGs) and from JSX. Full content inventory: [docs/frontend/product-catalog.md](../frontend/product-catalog.md).

## Card designs

Five original SVG card visuals (`assets/cards/banksphere-{platinum,cashback,travel,rewards,debit}.svg`) — badge/chip/contactless-symbol/masked-number/cardholder-placeholder, each themed from the existing design-token palette so the five read as one family. Network names (VISA/Mastercard/RuPay) are rendered as **plain text**, deliberately never as a redrawn version of a real network's trademarked logomark — see [design-system.md](../frontend/design-system.md#card-designs).

## Loan and FD demo rates

Used exactly the illustrative rates specified in this phase's instructions (Home 8.50% p.a., Personal 10.99% p.a., Car 9.25% p.a., Education 9.00% p.a.; FD up to 7.25% p.a., RD up to 6.75% p.a.) — not invented, not sourced from any real institution. Every rendering carries "Illustrative BankSphere demo rate. Actual rates may vary." Full table: [product-catalog.md](../frontend/product-catalog.md).

## Promotions

Four fictional campaigns (`Home.tsx`'s `PROMOTIONS` array, rendered via the new `PromotionBanner` component), each with an original SVG in `assets/promotions/` — go-digital, travel/rewards cards, home financing, grow savings. None copy any real bank's advertisement copy, imagery, or layout; the copy is the phase brief's own example text.

## Calculators

`LoanCalculator` (standard reducing-balance EMI formula) and `FixedDepositCalculator` (annual-compounding maturity formula) — both pure client-side, `useMemo`-derived, live-updating. **Numeric handling limitation, stated plainly:** both run on plain JS `number`s, since there's no `BigDecimal` equivalent available client-side without adding a new dependency (which this phase avoids). The mitigation actually in place: every value is `Math.round()`-ed before it's stored in state and always displayed through a new `formatINR()` helper (`Intl.NumberFormat("en-IN", ...)`), so no floating-point artifact (`12345.679999999`) is ever shown — only clean, rounded rupee amounts. This is fine for a fictional product's *estimate*, explicitly labeled as such; it would not be fine for the real backend's actual ledger, which correctly uses `BigDecimal`/`NUMERIC` throughout and is untouched by this phase.

## Chatbot implementation

`services/chatService.ts` — a mock, keyword-matched response generator, zero network calls, zero external credentials, isolated from `AuthContext` and every real BankSphere API. Six components under `components/chat/` (`ChatbotWidget`, `ChatWindow`, `ChatMessage`, `ChatInput`, `QuickQuestions`, `TypingIndicator`), mounted once globally in `App.tsx`. Full design: [docs/chatbot/architecture.md](../chatbot/architecture.md).

## Chatbot security boundary

Documented **before** any real capability exists, specifically so future work has a constraint to build against: the chatbot must never directly execute money movement, card/account actions, or auth changes; any future actionable request must route through intent-detection → an authenticated workflow → explicit user confirmation → the same banking API the UI already uses — never a chatbot-only code path. Sensitive data (passwords, PINs, CVVs, tokens, full account numbers) must never reach a future LLM. Full detail: [docs/chatbot/security.md](../chatbot/security.md).

## Design decisions

- **Routing collision resolved by namespacing, not by dropping either page.** This phase's brief asked for public product pages at `/cards` and `/loans` — paths the earlier Phase 2A/2B session had already given to *authenticated* "coming soon" placeholders. Rather than silently overwriting one or the other, the public catalog kept the natural path and the authenticated placeholders moved to `/banking/cards`/`/banking/loans` (pure frontend rename, no functionality existed to lose either way). Documented in detail, including the incidental fix it produces (a logged-out visitor clicking "Cards" in the header used to be bounced to `/login` for a stub — now lands on real content): [routing.md](../frontend/routing.md).
- **Header/hero overlap: inspected, not found.** The brief asked to fix an overlap between `PublicHeader` and the hero section. On inspection, `PublicHeader` already uses `position: sticky` (not `fixed`/`absolute`) inside `PublicLayout`'s normal flex-column flow, so it reserves its own space rather than floating over content — there is no structural way for it to overlap the hero. No visual QA was possible to confirm or deny a *rendered* overlap (no browser — see Problems below), so this is reported honestly as "verified correct by source inspection," not "bug fixed," since no bug was found. The header was still meaningfully enhanced per the rest of that instruction (a Personal/Business/NRI segment bar, a `Deposits` nav item, a visually stronger primary CTA with an icon).
- **Business/NRI segments shown, not linked.** The requested header structure includes `Personal / Business / NRI` as a segment row. Business and NRI have no corresponding content this phase. They're rendered as visible, honestly non-interactive labels (`Personal` marked active, `Business`/`NRI` muted with a "coming soon" `title`) rather than either omitted (losing the intended information architecture) or linked to nowhere (a broken link).
- **`Modal.tsx` finally has a real caller.** Built in the earlier Phase 2A/2B session with no consumer yet; this phase's `ProductDetailLayout` uses it for the "Apply for this card/loan"/"Open a Fixed Deposit" CTAs, since there's no application backend — clicking it opens an honest Coming Soon dialog rather than doing nothing or faking success.
- **`formatINR()` added rather than reusing `formatMoney()`.** The existing `formatMoney()` uses `en-US` grouping; all of this phase's content is explicitly INR-denominated demo data, so a dedicated `en-IN`-locale formatter (proper lakh/crore grouping, e.g. ₹25,00,000) was added instead of misusing the existing one or hacking locale strings inline.
- **A Tailwind dynamic-class bug caught before it shipped.** `ProductDetailLayout` originally built its key-facts grid column count as a template string (`` `grid-cols-${n}` ``) — which Tailwind's JIT scanner cannot see, since it only picks up complete literal class names present in source. Fixed with a small lookup map of literal, known-safe classes. Caught by re-reading the component after writing it, not by the build (which would have "succeeded" while silently producing unstyled output) — a reminder that a clean `tsc`/`vite build` doesn't catch every category of bug.

## Testing performed

- `npx tsc -b --force` — **0 errors**, run after the full addition (data layer, 5 card SVGs, 4 promo SVGs, ~14 new components, 6 chat components, 6 new pages, routing changes).
- `npx vite build` — **succeeded**, 197 modules (up from 159 before this phase), 364.6 kB JS / 109.2 kB gzip, 27.96 kB CSS / 5.86 kB gzip.
- Full route/link audit: every `path=` in `App.tsx` cross-checked against every static `to=`/`href=` and every dynamic `to={...}` template across the codebase — no orphaned or mismatched targets found (see the routing-collision fix above for the one real conflict, resolved before this audit).
- Quality scan: `console.log`/`debugger` — none; hardcoded secrets/API keys — none; external image/asset URLs (`src="http...`) — none, every asset is a local import; `<img>` tags without `alt` — none (verified individually, including intentional `alt=""` on decorative illustrations).
- Docker: rebuilt `banksphere/frontend:local`, swapped it into the still-running backend stack (customer/account/transaction-service + Postgres, up since the Phase 1 review), verified HTTP 200 on 20 distinct routes including every new card/loan/deposit slug and a nonexistent path (SPA fallback, correct), fetched the actual served JS bundle and grepped it for new-content strings ("BankSphere Assistant", "Find the right card for you") to confirm the running container is serving the new build rather than a stale cache, and re-confirmed CORS (`Access-Control-Allow-Origin: http://localhost:5173`) and all three backend health checks still pass after the swap.

**Not performed:** any test requiring an actual rendered browser — visual QA of the card designs, the calculators' interactive behavior, the chatbot's open/close/typing animation, FAQ accordion expand/collapse, responsive layout at the seven specified breakpoints, or keyboard navigation through the mobile menu/chat window/modal. All of these were designed for (semantic HTML, ARIA attributes, focus management, `:focus-visible`, alt text, responsive Tailwind classes) but not observed rendering. Stated plainly, not glossed over.

## Problems encountered and solutions

1. **The `/cards`/`/loans` routing collision** (see Design decisions above) — the single largest structural issue this phase introduced, caught by reading the existing `App.tsx` before writing new routes rather than after.
2. **No evidence of the claimed header/hero overlap** (see Design decisions above) — resolved by reporting what was actually found rather than fabricating a fix for an unconfirmed bug.
3. **The Tailwind dynamic-class bug** in `ProductDetailLayout` (see Design decisions above) — a class of bug that a successful build cannot catch, since Tailwind's JIT silently omits unrecognized dynamic class names rather than erroring.
4. **Nested `Card` inside `Card`.** `LoansCatalog`'s calculator section briefly wrapped `LoanCalculator` (which renders its own `Card` internally) in a second outer `Card` with a duplicate title — caught on review before the build, not left in.
5. **No browser, again** — same constraint as both earlier sessions today. Mitigated the same way: exhaustive static verification (type-check, build, full link audit, quality scan, container smoke test with actual bundle content verification) standing in for what a browser would confirm directly, with the gap stated explicitly rather than implied to be covered.

## Remaining limitations

- No visual/interactive QA — see Testing performed. This is the standing top priority if a browser becomes available before Phase 3.
- Calculators use plain JS floating-point numbers, mitigated by display-time rounding only — documented, not hidden (see product-catalog.md).
- Chatbot is fully mock — no LLM, no backend, no RAG. Architecture and security boundary for a real implementation are documented but nothing beyond the mock exists.
- No real application/account-opening flow for any product — every "Apply"/"Open" CTA is an honest Coming Soon modal.
- Business/NRI banking segments are visual-only, no dedicated content.

## What was learned

- A routing collision between "what a new phase asks for" and "what an earlier phase already built" is a real, recurring risk in incremental work — the fix here (check the existing route table before adding new top-level paths with the same names) is now the pattern to repeat.
- "The build succeeded" and "the page will render correctly" are not the same claim — Tailwind's JIT class scanning is a specific, easy-to-miss gap between them that's worth checking for explicitly (grep for template-string class construction) rather than assuming a clean build covers it.
- When an instruction describes a bug ("fix the header/hero overlap") that inspection doesn't confirm, the honest response is to say so and explain what was checked — not to either silently do nothing or perform a cosmetic change to look like something was fixed.

## Next phase

Per [docs/00-project-overview/roadmap.md](../00-project-overview/roadmap.md), Authentication remains the recommended next phase. Not started here, per explicit instruction — as is any backend work, any of Kafka/Redis/Kubernetes/Terraform/CI-CD/observability, and the React Native mobile app.
