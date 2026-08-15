# 2026-08-12 — Phase 3D: Dashboard Charts & Public Site Background

## Objective

Two requests handled together as one same-day pass: (1) add bar/pie/line charts to the app, and (2) make the public marketing site's background "more colorful, livable, modern and attractive." Both were scoped before implementation via clarifying questions, since each had a real decision only the user could make:

- **Background scope**: public marketing site only, not the authenticated banking app (banking UIs stay calmer for trust/readability — user confirmed this reasoning).
- **Chart placement/data**: the authenticated Dashboard, backed by real account/transaction data — not illustrative marketing charts.
- **Charting approach**: lightweight custom SVG components, zero new dependencies — consistent with this project's existing pattern (`Icon.tsx`/`Modal.tsx`/`Toast.tsx` were all hand-rolled for the same reason).

## Dataviz methodology

Before writing any chart code, the `dataviz` skill was loaded and followed in full: form choice before color, color assigned by job (categorical/sequential/status) and validated with `validate_palette.js` rather than eyeballed, then mark specs, interaction (hover/tooltip/table-view twin), and a final accessibility pass. BankSphere's own brand tokens were mapped onto the skill's generic parameters (not its default 8-hue reference palette) — `#0B5FA3` (brand primary) / `#C9962B` (brand accent) / `#0E9384` (brand secondary) as the 3-slot categorical set, validated: worst adjacent CVD ΔE 13.2 (protanopia), normal-vision floor ΔE 21.2, both clear of the skill's 8/15 thresholds. `#C9962B` sits below 3:1 contrast on white — every chart using it (the bar chart's Withdrawals series, the pie's second slice) ships a visible direct label as the required relief channel, not optional polish.

## What real data actually supports

The transaction data model is `{ transactionType: DEPOSIT | WITHDRAWAL | TRANSFER, amount, currency, createdAt, ... }` — no spending-category field exists (no "groceries/dining/utilities"). This ruled out a "spending by category" pie chart, which would have required inventing categories from free-text `description` fields — not something this project's honesty-about-data rules allow. Three charts that the real data model genuinely supports were built instead:

1. **Balance trend (line)** — the backend only exposes a *current* balance, not a balance-over-time series, so `Dashboard.tsx`'s `balanceTrend` reconstructs history by walking the already-fetched (newest-first) transaction list backward from today's balance, undoing each deposit/withdrawal. Every point is a real, derived value — verified with a standalone Node script covering 0/1/multi-transaction cases before wiring it into the component (see Verification below).
2. **Deposits vs Withdrawals (grouped bar)** — real transaction amounts summed per calendar day, capped to the most recent 8 active days.
3. **Balance by account (pie)** — real per-account balances, grouped by currency (never summed across currencies — see the repo's money-display rule) and **deliberately gated to 3+ accounts in a currency group**. A 1-slice pie is just the total and a 2-slice pie is a documented dataviz anti-pattern (a bar, or the two numbers, reads better) — most demo customers have exactly 2 accounts (Savings + Current), so this chart will often correctly not render; the existing balance cards already cover that case. This is expected behavior, not a bug — flagged explicitly here so it isn't mistaken for one later.

## Components added

`frontend/src/components/charts/{ChartCard,LegendSwatch,LineChart,BarChart,PieChart,chartColors}.tsx` — see `docs/frontend/components.md#componentscharts--dashboard-data-visualization` for the full contract of each. `Dashboard.tsx` was extended (not rewritten) with three `useMemo` transforms and a new "Financial overview" section between Quick Actions and Recent Transactions; every existing Dashboard element (balance cards, quick actions, recent transactions table) is untouched.

## Background treatment

See `docs/frontend/homepage-design.md#background-treatment-phase-3d` for the section-by-section rationale — a curated alternating rhythm of brand-token gradients plus two low-opacity blurred accent shapes (hero, trust section), deliberately not applied to every section to avoid the "visually noisy background" this project's own design-system guidance warns against.

## Verification performed

- `tsc -b && vite build` clean after every stage.
- A standalone Node script (not committed — scratch verification only) reproduced `Dashboard.tsx`'s transform logic and asserted correctness on five edge cases: zero transactions, a single deposit, a deposit+withdrawal sequence (confirming the reconstructed history is monotonically consistent with the deltas), same-day transaction aggregation, and the 2-vs-3-account pie gate. All five passed.
- The live Docker backend stack was not running at the time of this work (stopped since an earlier session — see the recurring "containers exit on environment restart" note in `progress.md`), so the charts were not exercised against a live customer's real API responses in this pass — only against the math in isolation and TypeScript's structural checks. This is stated plainly rather than glossed over, per this project's honesty-about-verification rule.
- No browser was available, so real rendering, hover/tooltip behavior, and the exact visual weight of the new background gradients were never visually observed — same constraint recorded in every prior frontend phase.

## What was deliberately not done

- No dark mode for the charts — this project has no dark mode anywhere yet (documented as out of scope in `design-system.md`), so only the light-mode palette was validated.
- No spending-by-category chart — see "What real data actually supports" above.
- No charts on the authenticated app's other pages (Accounts, Transactions) or the public marketing site — scoped to the Dashboard only, per the user's explicit answer.
- No new dependency — every mark is hand-authored SVG.
