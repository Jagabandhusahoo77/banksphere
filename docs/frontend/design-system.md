# BankSphere Design System

_Status: Phase 2A/2B — established alongside the professional banking UI. Source of truth is `frontend/tailwind.config.js` and `frontend/src/index.css`; this document explains the *why* behind those files._

## Branding

BankSphere's mark (`frontend/src/assets/branding/`) is an original geometric symbol: a rounded badge containing an abstract sphere (a circle) with an orbit ring around it, plus a small accent node riding the orbit's ascending edge.

- **Circle/sphere** — the "sphere" in BankSphere; also reads as a globe (digital, global banking).
- **Orbit ring** — motion/growth, and a shape that's visually distinct from any real bank's mark.
- **Accent node** — a small gold dot, used sparingly as the brand's "highlight" color throughout the product (e.g. promotional sections, quick-action bullets).

Three variants exist: `banksphere-logo.svg` (color, for light backgrounds), `banksphere-logo-white.svg` (monochrome, for dark backgrounds — header, footer, Login's brand panel), and `favicon.svg` (the badge alone, no wordmark). All are hand-authored SVG, not derived from any real bank's logo or template.

## Color tokens (`tailwind.config.js` → `theme.extend.colors`)

| Group | Tokens | Purpose |
|---|---|---|
| `brand` | `primary`, `primary-dark`, `primary-light`, `secondary`, `secondary-dark`, `accent`, `accent-light` | The BankSphere identity: deep blue as the dominant color (trust, stability), teal as a secondary accent (growth, digital), muted gold as a sparing highlight (premium moments — promotions, the logo's accent node) |
| `semantic` | `success`/`warning`/`error`/`info` (+ `-light` tint of each) | Status meaning — transaction status badges, form validation, toasts. Never reused for branding. |
| `surface` | `background`, `DEFAULT` (white), `muted`, `border` | Page background vs. card surface vs. subtle fills vs. hairline borders — every background/border in the app should be one of these four, not an arbitrary gray. |
| `ink` | `primary`, `secondary`, `muted`, `inverse` | Text color hierarchy — headings vs. body vs. de-emphasized captions vs. text-on-dark. |

**Rule:** components reference `brand-*`/`semantic-*`/`surface-*`/`ink-*` classes, not raw Tailwind palette colors (`slate-500`, `blue-600`, etc.) — this is what makes a future rebrand a one-file change in `tailwind.config.js` instead of a find-and-replace across every component.

## Typography

**Font:** a curated **system-font stack** (`-apple-system`, `Segoe UI`, `Roboto`, `Helvetica Neue`, `Arial`, `Noto Sans`), not a bundled webfont. See `frontend/src/assets/fonts/README.md` for why: every OS ships a highly legible, professional UI font already, and self-hosting a webfont would mean either fetching one from an external CDN (rejected — this project avoids external asset URLs that could disappear or fail in a restricted network) or fabricating a binary font file, which isn't something to pretend exists.

**Scale** (`tailwind.config.js` → `theme.extend.fontSize`):

| Token | Size | Use |
|---|---|---|
| `display` | 3rem / 700 | Homepage hero headline only |
| `h1` | 2.25rem / 700 | Page titles |
| `h2` | 1.75rem / 600 | Major section headings |
| `h3` | 1.375rem / 600 | Card titles, subsection headings |
| `body` | 1rem / 400 | Default paragraph/UI text |
| `body-sm` | 0.875rem / 400 | Secondary text, table cells |
| `caption` | 0.75rem / 400 | Metadata, timestamps, helper text |
| `label` | 0.8125rem / 500, wide tracking | Form labels, small uppercase eyebrows |

Applied as `text-display`, `text-h1`, etc. — never an arbitrary `text-[1.6rem]`.

## Spacing, radius, shadows

- **Spacing:** Tailwind's default 4px-based scale is used consistently rather than inventing a parallel system (a second scale on top of an already-consistent one would just be arbitrary values twice over). Two additions exist for page-section rhythm: `section` (5rem) and `section-sm` (3rem), used as vertical padding on public-site sections (`py-section`, `py-section-sm`).
- **Radius:** `theme.extend.borderRadius` overrides Tailwind's `sm`/`DEFAULT`/`md`/`lg` and adds `xl` and `pill`, so `rounded-md`/`rounded-lg`/`rounded-pill` map to the BankSphere scale everywhere automatically — no component needs an arbitrary radius value.
- **Shadows (elevation):** `shadow-elevation-1` through `shadow-elevation-4`, a 4-step scale from a resting card (`elevation-1`) to a modal (`elevation-4`). Named by elevation level, not by visual guess ("shadow-lg"), so the *meaning* (how "above the page" something is) stays consistent as the UI grows.

## Accessibility baked into the tokens

- `:focus-visible` is styled once, globally, in `index.css` (`ring-2 ring-brand-primary`) — every interactive element gets a consistent, visible focus ring without each component re-implementing it, and it's never suppressed.
- Semantic colors (`semantic-error` etc.) are used for both text and background pairings (`semantic-error` / `semantic-error-light`) chosen to keep sufficient contrast against white and against each other.

## Icons and illustrations

- **UI icons** (`components/common/Icon.tsx`): a single component with ~36 hand-authored 24×24 stroke paths (menu, close, chevrons, arrows, check, user, wallet, calendar, tag, umbrella, percent, car, graduation-cap, globe, star, etc.), rendered with `stroke="currentColor"` so they inherit text color automatically. Chosen over an icon-library dependency specifically to avoid adding a new package. Phase 3C added 8 new entries for the redesigned navigation/homepage — same style, same file, no new dependency.
- **Product/service icons** (`assets/icons/*.svg`): standalone SVG files (savings, current-account, card, personal/home/car/education-loan, investments, payments, UPI, statement, service-request, mobile-banking, bill-payment) used via `<img>` on marketing cards where a two-tone illustrative icon (not a monochrome UI icon) fits better. Unchanged in Phase 3C — the redesigned "Digital Banking Services" section reuses this existing set rather than adding near-duplicates (e.g. "Card Controls" reuses `card.svg`).
- **Illustrations** (`assets/illustrations/{accounts,payments,cards,loans,investments,security,hero,lifestyle,digital-banking,insights}/`): larger, more detailed original SVG scenes. All flat/geometric in style, built from basic shapes rather than freehand paths, and all use only the brand color tokens above — **including the Phase 3C "editorial illustration" additions**, which introduce simplified human figures (circle heads, rounded-shape torsos, dot eyes, no realistic faces) for the first time in this set, still fully abstract/geometric and never photographic. This is a deliberate choice: the redesign brief asked for realistic lifestyle photography, but this project has no licensed stock-photo source and cannot legally hotlink external images, so the existing hand-authored-SVG convention was extended with warmer, people-based scenes instead — see `docs/09-engineering-journal/` for the Phase 3C entry. New subfolders: `hero/` (hero scene, replaces the retired `assets/images/hero-banking.svg`), `lifestyle/` (Financial Goals banner), `digital-banking/` (Mobile Banking promo — a new folder distinct from the pre-existing `assets/icons/` two-tone icon set covering the same domain), `insights/` (Latest Insights category thumbnails). `assets/images/` now holds only the two genuinely generic, non-categorized illustrations (`coming-soon.svg`, `empty-state.svg`).
- **Promotional graphics** (`assets/promotions/*.svg`): four original scenes (one per homepage campaign — see [product-catalog.md](product-catalog.md)), same flat/geometric style as the other illustrations, not photographic or stock-image-like. Unchanged in Phase 3C.

## Card designs (`assets/cards/*.svg`)

Five original credit/debit card visuals (`banksphere-{platinum,cashback,travel,rewards,debit}.svg`), each ~400×252 (standard card aspect ratio), built from the same design-token palette rather than as one-off Photoshop-style renders:

- The BankSphere mark (a simplified badge — small rounded square + orbit rings, no wordmark) in a top corner, plus a chip glyph, a contactless symbol (three concentric arcs), a masked number (`•••• •••• •••• 4821`), placeholder cardholder name/expiry, and the card tier name.
- **Network names (VISA/Mastercard/RuPay) are rendered as plain text**, deliberately — not as a redrawn version of either network's actual trademarked logomark (e.g. Mastercard's interlocking circles). The network name is a factual label; the specific graphic mark is a protected trademark, so this phase only ever writes the word.
- Each card's color theme pulls directly from the token palette: `platinum` uses a graphite/slate gradient, `cashback` the brand secondary teal, `travel` the brand primary blue, `rewards` the brand accent gold, `debit` a light neutral surface tone — so the five cards read as one family, not five unrelated graphics.

## What was deliberately not done

- No dark mode / theme switching — out of scope for this phase, though the token structure (colors as named CSS-adjacent values in one config file) would make adding one straightforward later.
- No bundled webfont (see Typography above) — a real limitation, not a stylistic choice; documented so it isn't mistaken for an oversight.
- No redrawn card network logomarks (see Card designs above) — a deliberate IP-safety choice, not an oversight.
- No animation library (Phase 3C) — the redesigned homepage's hover/scroll animations use Tailwind transition utilities plus one small custom hook (`hooks/useInViewport.ts`), not framer-motion or similar, consistent with this project staying dependency-light. See [homepage-design.md](homepage-design.md).
- No real lifestyle photography (Phase 3C) — see the Illustrations note above.
