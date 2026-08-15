# Employee Operations

_Status: Phase 9B — the first real employee-initiated banking operation, cash deposit. Builds directly on Phase 9A's employee identity/RBAC foundation (`docs/architecture/employee-platform.md`) — that phase is unmodified baseline here, not redone. See [ADR-007](decisions/ADR-007-branch-cash-deposit.md) for the full design reasoning behind every decision summarized here._

## The employee operation channel

```
Employee Portal (browser)
    │  employee JWT
    ▼
employee-service — POST /api/v1/operations/cash-deposits   ("Employee Operations API")
    │  verifies: authenticated? CASH_DEPOSIT permission?
    │  forwards the employee's OWN bearer token
    ▼
account-service — POST /api/v1/accounts/{id}/employee-deposit
    │  verifies: employee token (own key)? CASH_DEPOSIT authority?
    │  branch scope: Account.ifsc == employee's branchIfsc (or broader-scope role)?
    │  account exists? ACTIVE? credits balance (same code path as customer deposit)
    │  forwards the employee's OWN bearer token
    ▼
transaction-service — POST /api/v1/transactions
    │  requires: a valid JWT (customer OR employee — unchanged design, ADR-001)
    ▼
Real DEPOSIT ledger entry, description "CASH DEPOSIT - Branch HQ001[, note]"

Customer Portal (separate browser session, customer JWT)
    → GET /api/v1/accounts/{id}         → sees the new balance immediately
    → GET /api/v1/transactions/account/{id} → sees the real CASH DEPOSIT entry
```

Every arrow forwards the acting principal's own bearer token to the next hop, which independently re-verifies it — the same "never trust an upstream service's word for who's calling, always re-derive from a verified token" rule this codebase has followed since Phase 1, now applied across a request that crosses employee-service → account-service → transaction-service instead of just terminating at one service.

## Employee authorization model

`CASH_DEPOSIT` is the existing Phase 9A permission (see `docs/architecture/employee-platform.md`'s role table) — not duplicated or renamed. Per the actual, inspected mapping: `TELLER` and `BRANCH_MANAGER` hold it; `KYC_OFFICER`, `LOAN_OFFICER`, `CARD_OFFICER`, `OPERATIONS`, and `ADMIN` do not. Enforced via `@PreAuthorize("hasAuthority('CASH_DEPOSIT')")` on both the employee-service endpoint (the primary, authoritative check) and the account-service endpoint (independent re-verification against the same, forwarded, cryptographically-verified token — not a rubber stamp). A customer JWT can never reach either check successfully: it fails employee-service's authentication outright (different signing key — see ADR-006), and even if forwarded raw to account-service, it authenticates as a *customer* principal there, which never carries a `CASH_DEPOSIT` authority.

## Branch authorization model

See [ADR-007, Decision 6](decisions/ADR-007-branch-cash-deposit.md#decision-6--branch-authorization-derived-from-accountifsc-not-an-invented-field) for the full reasoning. Summary: `Account` has no branch field of its own; `Account.ifsc` (Phase 8A) and `Branch.ifsc` (Phase 9A) already encode the same relationship a real IFSC encodes in an actual bank. A `TELLER`'s employee JWT now carries a `branchIfsc` claim; account-service's `employeeDeposit` compares it against the target account's own `ifsc` and rejects a mismatch with `403` (`BranchScopeViolationException`). `BRANCH_MANAGER`/`ADMIN` are exempted from this check — a documented broader-scope decision. With BankSphere's current single branch/single IFSC, this check is trivially satisfied today but is real, not a placeholder.

## API endpoints

| Endpoint | Service | Auth | Purpose |
|---|---|---|---|
| `GET /api/v1/operations/customer-search?accountNumber=…` or `?customerId=…` | employee-service | `ACCOUNT_VIEW` + `CUSTOMER_VIEW` | Resolve a customer + all their accounts, for the "find customer" step |
| `POST /api/v1/operations/cash-deposits` | employee-service | `CASH_DEPOSIT` | The operation itself — `{ accountId, amount, description? }` only |
| `GET /api/v1/operations/cash-deposits/history` | employee-service | `CASH_DEPOSIT` | The caller's own branch's recent deposits |
| `GET /api/v1/accounts/employee-lookup?accountNumber=…` | account-service | `ACCOUNT_VIEW` (employee token) | Full account detail by account number — employee-only, more revealing than the public `resolve-recipient` (ADR-005) |
| `GET /api/v1/accounts/employee-lookup/customer/{customerId}` | account-service | `ACCOUNT_VIEW` (employee token) | Every account for a given customer |
| `POST /api/v1/accounts/{id}/employee-deposit` | account-service | `CASH_DEPOSIT` (employee token) | The actual balance mutation |
| `GET /api/v1/customers/employee-lookup/{id}` | customer-service | `CUSTOMER_VIEW` (employee token) | Slim `{ id, firstName, lastName, status }` — no phone/email/address/DOB |

None of these are reachable by a customer token — see the authorization model above.

## Account-service responsibility

Unchanged: `Account` remains the sole owner of balance, status, currency, IFSC, and optimistic-locking version. `employeeDeposit` reuses the exact same `credit()`/`requireActive()` private helpers `deposit()` already used — not a parallel implementation. Customer-initiated deposit/withdraw/transfer are byte-for-byte unchanged this phase.

## Transaction-service responsibility

Unchanged: the sole ledger, `TXN-...` reference generation, `COMPLETED`/`FAILED` status. `POST /api/v1/transactions` already required nothing beyond "a valid JWT" (ADR-001) — recognizing an employee-signed token as satisfying that is not a new relaxation, since it never checked identity beyond token validity in the first place.

## Customer portal integration

No code changed in `frontend/`. The customer portal already polls real balance/transaction data from account-service/transaction-service on every visit to Accounts/Transactions — a cash deposit performed by an employee is just another real backend mutation those existing, unmodified reads pick up the next time the customer loads the page. Verified live (see the Phase 9B engineering journal entry) via a real second browser session: the customer's balance and a `CASH DEPOSIT` transaction entry appeared with no customer-portal code changes at all.

## Security boundaries

- Employee JWTs (`EMPLOYEE_JWT_SECRET`) remain cryptographically distinct from customer JWTs (`JWT_SECRET`) — unchanged from ADR-006. Three more services (account/transaction/customer) now know how to *validate* an employee token (via their own `EmployeeJwtValidator`, same secret), but none of them can *issue* one — only employee-service does that.
- Every existing customer-facing endpoint's authentication code is untouched. New employee-only endpoints are additive, gated by `@PreAuthorize` against permissions no customer token can carry.
- `CashDepositRequest` structurally cannot carry `employeeId`/`branchId`/`customerId` — there are no such fields on the type. See ADR-007, Decision 7.

## Consistency model

No distributed transaction. Account-service's own local `@Transactional` method (one balance write, `@Version`-protected) is the sole atomicity boundary. Ledger recording is best-effort, exactly as it already was for customer deposits (ADR-001) — this phase preserves that trade-off rather than reopening it. See ADR-007, Decisions 4–5.

## Audit preparation

`EmployeeAuditLog` gained `cashDepositStarted`/`cashDepositSucceeded`/`cashDepositFailed`, extending Phase 9A's structured-logging pattern with `branchId`/`accountId`/`amount` fields. Still not the real Audit Service — no Kafka, no new external system. See ADR-007, Decision 9.

## Future Kafka integration

None of this phase's synchronous call chain (employee-service → account-service → transaction-service) is expected to change shape when Kafka eventually arrives — the natural integration point is transaction-service (or a future Audit Service) additionally publishing an event after a ledger entry is recorded, exactly as already anticipated for customer-initiated transactions. Cash deposit doesn't need its own separate eventing story.
