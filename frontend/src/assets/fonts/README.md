# Fonts

BankSphere currently uses a **native system-font stack** (`-apple-system`, `Segoe UI`, `Roboto`, `Helvetica Neue`, `Arial`, `Noto Sans` — see `tailwind.config.js` → `theme.extend.fontFamily.sans`), not a bundled webfont.

This directory is kept as the intended home for a self-hosted brand typeface (e.g. `.woff2` files) if one is adopted later, but no font files are checked in yet — deliberately, not as an oversight:

- No real font binary can be produced without either fetching a third-party font file over the network (Google Fonts or similar) or shipping a font the project doesn't have a license/source for. Neither is appropriate to fabricate.
- The system-font stack avoids an external network request per page load (consistent with the "no random external URLs that may disappear" rule applied to fonts as well as images) and every fallback in the stack is already a professional, highly-legible UI typeface (San Francisco, Segoe UI, Roboto).

**If a bundled brand typeface is adopted in a later phase:** add the licensed `.woff2` file(s) here, `@font-face` them in `src/index.css`, and prepend the family name to `tailwind.config.js`'s `fontFamily.sans` array (keep the system stack as the fallback chain).
