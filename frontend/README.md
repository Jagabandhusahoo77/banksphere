# Frontend

The customer-facing web application for BankSphere.

**Stack:** React, TypeScript, Vite, React Router, Axios, Tailwind CSS

## Structure

```text
src/
├── assets/       Branding (logo/favicon), icons, illustrations, images — see docs/frontend/design-system.md
├── components/
│   ├── common/       Button, Input, Select, Badge, Card, Modal, Toast, Skeleton, EmptyState, ErrorState, Spinner, Icon, ComingSoonPage
│   ├── navigation/   Logo, PublicHeader, BankingHeader, BankingSidebar, MobileNavigation, Footer
│   ├── banking/      AccountCard, BalanceCard, TransactionRow/Table, QuickAction, ServiceCard, SecurityBanner
│   └── forms/        FormField, AmountInput
├── context/      AuthContext — placeholder session state
├── hooks/        useAsync, useCustomer, useAccounts, useTransactions
├── layouts/      PublicLayout (marketing site) + AppLayout (responsive internet-banking shell)
├── pages/        Route-level views — public/ (Home, About, Contact, NotFound), auth/, dashboard/, accounts/ (list + details), transactions/, plus cards/loans/payments/investments/profile/support (placeholders)
├── routes/       ProtectedRoute — redirects to /login when not authenticated, preserving the intended destination
├── services/     API client modules (Axios-based) — unchanged from Phase 1, see below
├── types/        Shared TypeScript types matching backend DTOs
└── utils/        formatMoney / formatDateTime / maskAccountNumber / accountTypeLabel / timeOfDayGreeting
```

Full breakdown of every component: [docs/frontend/components.md](../docs/frontend/components.md). Design tokens and branding: [docs/frontend/design-system.md](../docs/frontend/design-system.md). `tests/` holds frontend test suites — no test framework wired up yet (unchanged; only backend tests are in scope so far).

## Status

**Phase 1 (functional) + Phase 2A/2B (professional banking UI) implemented.** A public marketing site (`/`, `/about`, `/contact`) and an authenticated internet-banking app (`/dashboard`, `/accounts`, `/accounts/:id`, `/transactions`, plus six "coming soon" feature routes), backed by the three Phase 1 backend services through an unchanged Axios-based API layer in `src/services/`. No component makes API calls directly — all HTTP calls go through `src/services/*Service.ts`.

Authentication does not exist yet on the backend (see `backend/services/customer-service`), so **Login is still a placeholder**, now with a professional visual design: it accepts a customer ID (UUID), verifies it exists via `GET /api/v1/customers/{id}`, and stores it in `localStorage` to establish a local-only session (`AuthContext`). There is no password, token, or server-side session — this will be replaced once `auth-service` exists in a later phase.

## Prerequisites

- Node.js 20+ and npm
- The three backend services running (see [`backend/README.md`](../backend/README.md)) — the frontend has nothing to display without them

## Running locally

```bash
cd frontend
cp .env.example .env   # adjust service URLs if needed
npm install
npm run dev
```

The dev server runs at `http://localhost:5173` by default (see `vite.config.ts`).

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `VITE_CUSTOMER_SERVICE_URL` | `http://localhost:8081` | Base URL for customer-service |
| `VITE_ACCOUNT_SERVICE_URL` | `http://localhost:8082` | Base URL for account-service |
| `VITE_TRANSACTION_SERVICE_URL` | `http://localhost:8083` | Base URL for transaction-service |

Vite inlines these at build time, so when building the Docker image (see `Dockerfile`) they must be passed as build args, not just runtime env vars — `docker/local/docker-compose.yml` does this already.

## Building

```bash
npm run build     # type-checks with tsc -b, then builds to dist/
npm run preview   # serve the production build locally
```

## Signing in

You need a real customer ID to sign in. Create one first:

```bash
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","phone":"+15550100","dateOfBirth":"1990-05-20","address":"123 Main St"}'
```

Copy the `id` from the response and paste it into the login screen.

## Tests

No frontend test framework is set up yet (see `tests/`) — this was intentionally out of scope for Phase 1, which required backend unit/controller tests only. `npm run build`'s TypeScript check (`tsc -b`) is the current correctness gate for the frontend.
