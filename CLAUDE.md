# BankSphere Development Rules

Permanent rules for working on this repository. These apply across every phase, not just the current one — read [docs/00-project-overview/scope.md](docs/00-project-overview/scope.md) for what's actually in scope *right now*.

## Fictional project

BankSphere is a fictional, educational project. It is not affiliated with, endorsed by, or connected to any real bank.

- Do not copy ICICI Bank (or any other real bank's) proprietary branding, logos, colors, wordmarks, or other protected assets. Original visual identity only.
- The frontend has an original, professional banking visual identity (logo, color palette, type scale — see [docs/frontend/design-system.md](docs/frontend/design-system.md)), designed from scratch and not derived from any real institution's brand. Extend it; don't introduce a second, inconsistent visual language alongside it.
- No real customer data, ever. All customers, accounts, and transactions in this repo are synthetic.

## Money

- **Never use floating-point types (`float`, `double`) for money.** Use `BigDecimal` in Java and `NUMERIC`/`DECIMAL` in PostgreSQL, everywhere, with no exceptions.
- On the frontend, treat monetary values as opaque numbers for display/formatting only (see `frontend/src/utils/format.ts`) — never do floating-point arithmetic on them client-side for anything that affects a real balance.

## Secrets and credentials

- Never hard-code real secrets, passwords, tokens, or API keys in source code.
- Never commit a real `.env` file or real credentials — only commit `.env.example` files with placeholder/local-dev-only values (see `docker/local/.env.example`).
- Local-development-only default credentials (e.g. `banksphere` / `banksphere_local_dev` in `docker-compose.yml`) are acceptable and expected — they are not real secrets, but treat any value that grants access to a real system as one.

## Architecture

- **Controllers stay thin.** No business logic in `@RestController` classes — they validate input (via `@Valid`), delegate to a service, and translate the result to an HTTP response. Business logic belongs in the `service/` package.
- **Use DTOs, never expose JPA entities directly** through an API. Request/response shapes live in `dto/`; entities live in `entity/` and never leave the service layer.
- **Use Flyway migrations** for all schema changes — never rely on Hibernate's `ddl-auto` to create or alter a production-shaped schema (`ddl-auto: validate` only). Never edit an already-applied migration; add a new versioned migration instead (e.g. `V2__...sql`), even for something as small as dropping a redundant index.
- **Write tests.** New service-layer logic gets a JUnit 5 + Mockito unit test; new controller behavior gets a `@WebMvcTest` test. Tests for money-affecting logic (deposits, withdrawals, balance checks) must cover both the success path and the rejection path (invalid amount, insufficient balance, inactive account, not found).
- **Document significant architectural decisions** as an ADR under `docs/architecture/decisions/` (see [ADR-001](docs/architecture/decisions/ADR-001-account-transaction-consistency.md) for the expected shape: Status, Context, Current Design, Problem, Why it's acceptable now, Future Design). A decision to *not* fix something during a review, and why, belongs in an ADR or the engineering journal just as much as a decision to fix something.

## Security

- **Never trust an ID from the URL, path variable, or request body for an access decision.** Every customer/account/transaction lookup or mutation must re-derive "whose data is this?" from the caller's own verified identity (the JWT) and check it against the resource being accessed. See [docs/security/authorization.md](docs/security/authorization.md).
- **Never hardcode a JWT signing secret, and never let it fall below 32 bytes for HS256.** Read it from `JWT_SECRET` (see `docker/local/.env.example` for the local-dev-only default) — each service's JWT config class should fail fast at startup if the configured secret is too short, not fail silently or accept a weak one.
- **`401` means "who are you?", `403` means "I know who you are, and no."** Don't conflate them — a missing/invalid/expired token is `401`; a valid token for the wrong customer is `403`. See [docs/security/authorization.md](docs/security/authorization.md).
- **Never let a login (or similar) endpoint's response reveal whether an identifier exists.** Same status code, same message, same rough response time for "wrong password" and "unknown email" — see [docs/security/authentication.md#no-account-enumeration](docs/security/authentication.md#no-account-enumeration) for the timing-safe comparison pattern.
- **`@WebMvcTest` does not auto-detect a custom `@EnableWebSecurity` `@Configuration` class, and `@AutoConfigureMockMvc(addFilters = false)` disables more than it looks like.** Disabling filters also disables the Spring Security filter that populates `HttpServletRequest.getUserPrincipal()` — which is what a plain `Authentication`-typed controller parameter resolves from — so it silently comes back `null` instead of denying the request, and any code that calls `.getName()` on it throws an unexplained `500` instead of the intended `401`/`403`. For a controller test that needs the real security chain's behavior (which endpoints are public, ownership-check status codes), explicitly `@Import` the service's `SecurityConfig` (and any plain `@Component` it depends on, like a `AuthenticationEntryPoint`/`AccessDeniedHandler`) and mock only the JWT parsing step, rather than disabling the filter chain. See the Phase 3A engineering journal entry for the full diagnosis.
- **A password, hash, or other secret must never be reachable from a response DTO** — not "redacted before serialization," but structurally absent from the type being serialized, so there's no redaction step to forget. See `CustomerResponse`/`CustomerSummary`/`AuthResponse` for the pattern.

## Frontend

- **Use the design system, not arbitrary values.** Colors, type sizes, radii, shadows, and spacing come from `tailwind.config.js`'s `brand`/`semantic`/`surface`/`ink` tokens and type/radius/shadow scale (see [docs/frontend/design-system.md](docs/frontend/design-system.md)) — not raw Tailwind palette classes (`slate-500`, `blue-600`) or one-off arbitrary values (`text-[1.6rem]`). A rebrand should be a `tailwind.config.js` change, not a find-and-replace across every component.
- **Reuse components before writing new markup.** Check `docs/frontend/components.md` and `frontend/src/components/{common,navigation,banking,forms}/` before hand-rolling a button, input, table, badge, or empty/error/loading state — these exist specifically so five pages don't each grow their own slightly-different version.
- **Don't add a UI library dependency for something a small, self-authored component already covers.** `Icon.tsx`, `Modal.tsx`, and `Toast.tsx` were built from scratch specifically to avoid adding an icon-library/dialog/toast package — keep extending those rather than introducing a new dependency for the same job.
- **Never fake a feature that has no backend.** A route/nav-entry for an unimplemented feature (payments, cards, loans, investments, etc.) renders `ComingSoonPage` — real UI, honest state, no fabricated data and no form that pretends to submit somewhere real. See the Contact page's message form for the pattern when a professional-looking form is still warranted (real client-side validation, but an explicit "this wasn't actually sent" disclosure instead of a fake network call).
- **Handle money display correctly, not just plausibly.** e.g. never sum balances across different currencies into one "total" — group by currency first (see Dashboard's `totalsByCurrency`). A demo-looking number that's financially wrong is still wrong.
- **Accessibility is not optional per-component.** Every interactive element needs a visible `:focus-visible` state (handled globally in `index.css` — don't override it away), form inputs need an associated `<label>`, icon-only buttons need `aria-label`, and decorative images get `alt=""` while meaningful ones get real alt text.
- **Any demo rate, fee, limit, reward, or offer must be clearly labeled as fictional.** "Illustrative BankSphere demo rate. Actual rates may vary." (or equivalent) on every rendering, not just the first — see `docs/frontend/product-catalog.md` for the exact numbers already in use for cards/loans/deposits. Never present fictional data as real market data, and never invent a new rate without adding it to `frontend/src/data/` with the same disclaimer treatment as its neighbors.
- **Card/payment network marks (VISA, Mastercard, RuPay, etc.) are rendered as plain text, never as a redrawn logomark.** The network name is a factual label; the specific graphic mark (e.g. Mastercard's interlocking circles) is a protected trademark — see `docs/frontend/design-system.md#card-designs`.
- **Tailwind class names must be complete string literals in source.** `` `grid-cols-${n}` `` or similar template-string construction is invisible to Tailwind's JIT scanner and silently produces unstyled output — a clean `tsc`/`vite build` will not catch this. Use a lookup map of literal classes keyed by the dynamic value instead (see `ProductDetailLayout.tsx`'s `KEY_FACT_GRID_CLASSES` for the pattern).
- **Check the existing route table before adding new top-level paths.** A new phase's requested routes can collide with an earlier phase's — when they do, resolve by renaming the *less real* one (a stub/placeholder) to a namespaced path (e.g. `/banking/cards`) rather than silently dropping either page. Document the collision and the resolution (see `docs/frontend/routing.md`), don't just quietly rename and move on.
- **The chatbot (or any future assistant) must never directly execute a banking action** — no transfers, withdrawals, deposits, beneficiary changes, password/PIN changes, card blocking, loan approval, or account modification. Required flow for anything action-like: intent detection → an authenticated, existing workflow screen → explicit user confirmation → the same banking API the UI already uses. Never send passwords, PINs, CVVs, tokens, or unnecessary full account numbers to it or through it. Full detail: `docs/chatbot/security.md`.

## Scope discipline

- **Do not introduce future-phase technologies early.** Check [docs/00-project-overview/scope.md](docs/00-project-overview/scope.md) before adding a dependency or piece of infrastructure. As of this writing that means: no Kafka, Redis, Kubernetes, Terraform, AWS infrastructure, CI/CD pipelines, Argo CD/GitOps, or observability stack (Prometheus/Grafana/OpenTelemetry/OpenSearch) until their designated phase.
- **Do not modify unrelated files.** A change scoped to one service or one concern should not touch files outside that scope without a stated reason.
- Don't add functionality nobody asked for. A bug fix doesn't need surrounding refactors; a one-shot operation doesn't need a reusable abstraction.

## Honesty about verification

- **Do not claim tests passed without actually running them.** If a test suite couldn't be run (missing Java/Maven, missing Docker Compose, no browser available, etc.), say exactly what's missing and what was verified instead, rather than asserting success. See [docs/09-engineering-journal/](docs/09-engineering-journal/) for examples of this in practice — environment constraints are recorded, not glossed over.
- A tool that doesn't enforce a browser behavior (e.g. `curl` for CORS) passing is not proof that the browser-facing behavior works. State what was actually exercised.

## Where to look for more context

- [docs/00-project-overview/](docs/00-project-overview/) — scope, roadmap, current progress.
- [docs/architecture/](docs/architecture/) — how the system fits together, plus ADRs for significant decisions.
- [docs/database/](docs/database/) — schema per service.
- [docs/api/](docs/api/) — endpoint reference, kept in sync with the actual implementation.
- [docs/frontend/](docs/frontend/) — design system, frontend architecture, routing, component library, product catalog.
- [docs/chatbot/](docs/chatbot/) — chatbot architecture, security boundary, roadmap.
- [docs/security/](docs/security/) — authentication, authorization, JWT implementation, threat model.
- [docs/09-engineering-journal/](docs/09-engineering-journal/) — dated entries recording what was actually done, problems hit, and how they were resolved.
