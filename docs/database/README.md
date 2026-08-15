# Database Structure

_Status: covers all six databases backing customer-service, account-service, transaction-service, beneficiary-service, employee-service, and (Phase 9C) kyc-service. See the root [README](../../README.md) for the full phased plan._

## Model: database per service

BankSphere uses a database-per-service model. For local development all three databases live in a single PostgreSQL 16 instance (see `docker/local/docker-compose.yml` and `docker/postgres/init/`), but each service:

- connects only to its own database (`DB_NAME` environment variable),
- owns and versions its own schema via Flyway migrations (`src/main/resources/db/migration/`),
- never reads or writes another service's tables directly.

In a production deployment each database would be a separate managed instance (e.g. separate RDS instances).

## `banksphere_customer` (customer-service)

### `customers`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `first_name` | `VARCHAR(100)` | Not null |
| `last_name` | `VARCHAR(100)` | Not null |
| `email` | `VARCHAR(255)` | Not null, unique (case-insensitive — enforced via `CREATE UNIQUE INDEX ... ON customers (LOWER(email))`, not a plain column constraint) |
| `phone` | `VARCHAR(20)` | Not null |
| `date_of_birth` | `DATE` | Not null |
| `address` | `VARCHAR(500)` | Not null |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |
| `created_at` | `TIMESTAMPTZ` | Set on insert |
| `updated_at` | `TIMESTAMPTZ` | Set on insert and update |

Migration: `V1__create_customers_table.sql`. `V2__add_authentication.sql` added `customer_credentials` (Phase 3A, unchanged this phase).

### `otp_challenges` (Phase 9D)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `identifier` | `VARCHAR(255)` | Not null — the email/phone the OTP was requested for |
| `purpose` | `VARCHAR(40)` | `LOGIN`, `STEP_UP_TRANSFER`, `STEP_UP_WITHDRAWAL`, `STEP_UP_BENEFICIARY`, `STEP_UP_PROFILE_CHANGE` (`CHECK` constraint) |
| `customer_id` | `UUID` | Nullable, no FK — `null` means `identifier` never matched a real, eligible customer (see enumeration prevention, [ADR-009](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-7--account-enumeration-prevention-extending-an-existing-pattern-rather-than-inventing-a-new-one)) |
| `otp_hash` | `VARCHAR(255)` | Not null — BCrypt hash; the plaintext OTP is never stored |
| `context_hash` | `VARCHAR(64)` | Nullable — SHA-256 hex digest binding a step-up challenge to its exact operation; `null` for `LOGIN` |
| `status` | `VARCHAR(20)` | `PENDING`, `VERIFIED`, `EXECUTED`, `EXPIRED`, `LOCKED`, `CONSUMED` (`CHECK` constraint) |
| `attempt_count` | `INTEGER` | Not null, default `0` |
| `max_attempts` | `INTEGER` | Not null, default `5` |
| `expires_at` | `TIMESTAMPTZ` | Not null |
| `consumed_at` | `TIMESTAMPTZ` | Nullable |
| `requested_ip` | `VARCHAR(64)` | Nullable — for rate limiting/audit only, never used for an authorization decision |
| `created_at` | `TIMESTAMPTZ` | Set on insert |

Indexed on `(identifier, purpose, created_at)` (the resend-cooldown/superseding lookup) and `created_at DESC` (the dev OTP inbox).

### `refresh_tokens` (Phase 9D)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `customer_id` | `UUID` | Not null, no FK |
| `token_hash` | `VARCHAR(64)` | Not null, **unique** — SHA-256 hex digest of the opaque plaintext token; see [ADR-009, Decision 11](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-11--refresh-token-strategy-opaque-httponly-cookie-only-rotation-with-reuse-detection) for why SHA-256, not BCrypt |
| `status` | `VARCHAR(20)` | `ACTIVE`, `ROTATED`, `REVOKED` (`CHECK` constraint) |
| `replaced_by_token_id` | `UUID` | Nullable — set when rotated, points at its successor |
| `expires_at` | `TIMESTAMPTZ` | Not null (default 14 days from issuance) |
| `revoked_at` | `TIMESTAMPTZ` | Nullable |
| `created_at` | `TIMESTAMPTZ` | Set on insert |

Indexed on `(customer_id, status)` (the family-wide revocation lookup). Migration: `V3__add_otp_challenges.sql` (both tables).

## `banksphere_account` (account-service)

### `accounts`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `customer_id` | `UUID` | Not null — references a customer by id; not a DB-level FK (separate database) |
| `account_number` | `VARCHAR(20)` | Not null, unique, system-generated |
| `ifsc` | `VARCHAR(11)` | Not null (Phase 8A) — same value on every account; see below |
| `account_type` | `VARCHAR(20)` | `SAVINGS`, `CURRENT` |
| `balance` | `NUMERIC(19,4)` | Not null, `>= 0`, never a floating-point type |
| `currency` | `VARCHAR(3)` | ISO 4217 code, e.g. `USD` |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE`, `CLOSED` |
| `version` | `BIGINT` | Optimistic locking column (JPA `@Version`), guards concurrent balance updates |
| `created_at` | `TIMESTAMPTZ` | Set on insert |
| `updated_at` | `TIMESTAMPTZ` | Set on insert and update |

Migrations: `V1__create_accounts_table.sql` (indexed on `customer_id` for `GET /api/v1/accounts/customer/{customerId}`), `V2__add_ifsc_to_accounts.sql`. `ifsc` is set server-side to the single constant `AccountServiceImpl.BANKSPHERE_IFSC` (`BANK0000001`) on every account — BankSphere is one fictional bank with no branch model, so IFSC identifies the bank, not the individual account, and is never randomized or client-suppliable. `V2` adds the column as nullable, backfills existing rows, then locks it to `NOT NULL` with a format `CHECK` — safe against a table that already has data (verified live: applied cleanly against 33 real pre-existing account rows from earlier phases, all correctly backfilled to `BANK0000001`, zero left `NULL`).

**Phase 7A note:** the internal transfer operation (`POST /api/v1/accounts/transfer`) added no new migration and no new column — it's a third balance-mutating operation (alongside deposit/withdraw) on this same `accounts` table, using the existing `version` column for optimistic-locking protection exactly as deposit/withdraw already did. See [ADR-004](../architecture/decisions/ADR-004-internal-account-transfer.md).

### `transfer_idempotency_records` (Phase 9D)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `customer_id` | `UUID` | Not null, no FK |
| `idempotency_key` | `VARCHAR(100)` | Not null — client-generated, opaque |
| `status` | `VARCHAR(20)` | `IN_PROGRESS`, `COMPLETED`, `FAILED` (`CHECK` constraint) |
| `transfer_id` | `UUID` | Nullable — set on `COMPLETED`, the resulting `TransferResponse.transferId` |
| `response_snapshot` | `TEXT` | Nullable — the serialized `TransferResponse` JSON, replayed verbatim on a retry once `COMPLETED` |
| `created_at` | `TIMESTAMPTZ` | Set on insert |
| `completed_at` | `TIMESTAMPTZ` | Nullable |

`UNIQUE INDEX uq_transfer_idempotency_customer_key ON (customer_id, idempotency_key)` — this index, not the application-layer lookup that precedes it, is the actual exactly-once enforcement: a concurrent second request with the same key loses a race against Postgres itself, surfaced as `IdempotencyConflictException` (`409`) rather than a silent duplicate. See [ADR-009, Decision 15](../architecture/decisions/ADR-009-customer-otp-and-step-up-authentication.md#decision-15--idempotency-belongs-to-account-service-not-the-otpauth-layer). Migration: `V3__add_transfer_idempotency.sql`.

## `banksphere_transaction` (transaction-service)

### `transactions`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `transaction_reference` | `VARCHAR(40)` | Not null, unique, system-generated (`TXN-...`) |
| `account_id` | `UUID` | Not null — references an account by id; not a DB-level FK |
| `transaction_type` | `VARCHAR(20)` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER` |
| `amount` | `NUMERIC(19,4)` | Not null, `> 0` |
| `currency` | `VARCHAR(3)` | ISO 4217 code |
| `status` | `VARCHAR(20)` | `PENDING`, `COMPLETED`, `FAILED` |
| `description` | `VARCHAR(500)` | Optional |
| `created_at` | `TIMESTAMPTZ` | Set on insert; immutable (no `updated_at` — transactions are append-only) |

Migrations: `V1__create_transactions_table.sql`, `V2__drop_redundant_account_id_index.sql`. Indexed on `(account_id, created_at DESC)` for paginated, newest-first history lookups — this single composite index also serves plain `account_id` equality lookups as a leading-column prefix, so the Phase 1 review dropped the separate single-column `account_id` index (`V2`) as redundant rather than leaving both.

## `banksphere_beneficiary` (beneficiary-service)

### `beneficiaries`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `customer_id` | `UUID` | Not null — references a customer by id; not a DB-level FK (separate database) |
| `beneficiary_name` | `VARCHAR(100)` | Not null |
| `account_number` | `VARCHAR(20)` | Not null — the beneficiary's own account number, at whichever bank they hold it (not necessarily BankSphere) |
| `ifsc` | `VARCHAR(11)` | Not null — standard Indian IFSC format |
| `bank_name` | `VARCHAR(150)` | Not null |
| `nickname` | `VARCHAR(50)` | Optional |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `created_at` | `TIMESTAMPTZ` | Set on insert |
| `updated_at` | `TIMESTAMPTZ` | Set on insert and update |

Migration: `V1__create_beneficiaries_table.sql`. No `version` optimistic-locking column, unlike `accounts` — that column exists specifically to guard concurrent *balance* mutations; a beneficiary's fields are never concurrently incremented/decremented the way a balance is, so the extra column would be complexity with no corresponding risk it protects against.

**Indexes:**
- `idx_beneficiaries_customer_id` on `(customer_id)` — the "list my beneficiaries" query's access path.
- `uq_beneficiaries_customer_account_ifsc_active`, a **partial unique index** on `(customer_id, account_number, ifsc) WHERE status = 'ACTIVE'` — enforces "a customer can't have two active beneficiaries with the same account number + IFSC" at the database level, closing the race a concurrent duplicate `POST` could otherwise slip through if this were only an application-layer check (see `BeneficiaryServiceImpl.createBeneficiary`). Deliberately **partial** (scoped to `ACTIVE` rows only) rather than a plain unique constraint: a customer can deactivate a beneficiary and later re-add the identical account/IFSC combination, which a whole-table constraint would permanently block. This composite already covers `customer_id` and `customer_id + account_number` lookups as leading-column prefixes, so no separate bare index on either `account_number` or `ifsc` alone was added — neither is queried outside this composite (a "find beneficiaries by account number across all customers" query would itself be a cross-customer data-exposure risk, not something this service does).

## `banksphere_employee` (employee-service, Phase 9A)

A fifth, independent database — no relation to (and no foreign key toward) any of the four above. See [ADR-006](../architecture/decisions/ADR-006-employee-identity-and-rbac.md) and [docs/architecture/employee-platform.md](../architecture/employee-platform.md).

### `branches`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `branch_code` | `VARCHAR(20)` | Not null, unique |
| `branch_name` | `VARCHAR(150)` | Not null |
| `ifsc` | `VARCHAR(11)` | Not null |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | |

### `employees`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `employee_number` | `VARCHAR(20)` | Not null, unique |
| `username` | `VARCHAR(50)` | Not null, unique |
| `password_hash` | `VARCHAR(255)` | Not null — BCrypt, never plaintext |
| `first_name` / `last_name` | `VARCHAR(100)` | Not null |
| `email` | `VARCHAR(254)` | Not null |
| `branch_id` | `UUID` | Not null, DB-level FK to `branches.id` — a real FK, unlike the cross-service references below, since `branches` and `employees` live in the same database |
| `status` | `VARCHAR(20)` | `ACTIVE`, `INACTIVE`, `LOCKED` |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | |

**Indexes:** `idx_employees_branch_id` on `(branch_id)`; `idx_employees_username` on `(username)` — the login lookup's access path.

### `employee_roles`

| Column | Type | Notes |
|---|---|---|
| `employee_id` | `UUID` | FK to `employees.id`, `ON DELETE CASCADE` |
| `role` | `VARCHAR(30)` | One of the 7 fixed `Role` values (`CHECK` constraint) |

Composite primary key `(employee_id, role)` — the many-to-many join table backing `Employee.roles`. No separate `roles` reference table: roles are a fixed, code-defined enum in this phase (see `security/RolePermissions`'s own doc comment), not a database-administrable set.

### `cash_deposit_operations` (Phase 9B)

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `operation_reference` | `VARCHAR(20)` | Not null, unique — this service's own `CD-...` reference, never transaction-service's `TXN-...` |
| `employee_id` | `UUID` | Not null, FK to `employees.id` |
| `employee_number` | `VARCHAR(20)` | Not null |
| `branch_id` | `UUID` | Not null, FK to `branches.id` |
| `branch_code` | `VARCHAR(20)` | Not null |
| `customer_id` | `UUID` | Not null — references a customer by id; not a DB-level FK (separate database) |
| `account_id` | `UUID` | Not null — references an account by id; not a DB-level FK (separate database) |
| `account_number` | `VARCHAR(20)` | Not null — captured at operation time (immutable once assigned, see below) |
| `amount` | `NUMERIC(19,4)` | Not null |
| `currency` | `VARCHAR(3)` | Not null |
| `status` | `VARCHAR(20)` | `COMPLETED` or `FAILED` — in practice only `COMPLETED` rows are ever written (see below) |
| `transaction_reference` | `VARCHAR(30)` | Nullable — the real transaction-service `TXN-...` reference, absent if best-effort ledger recording failed |
| `failure_reason` | `VARCHAR(500)` | Nullable, reserved — not currently populated (see below) |
| `created_at` | `TIMESTAMPTZ` | Set on insert |

Migration: `V3__create_cash_deposit_operations_table.sql`. **Deliberately stores only immutable, point-in-time operational facts** — which account number was credited, by how much, by whom, when — never mutable state like account balance or a customer's display name, both of which are always re-fetched live from their owning service instead (see `docs/architecture/employee-operations.md` and [ADR-007, Decision 9](../architecture/decisions/ADR-007-branch-cash-deposit.md#decision-9--audit-and-operational-history-structured-logs-phase-9as-pattern-extended-plus-one-minimal-carefully-scoped-table)). Only **successful** deposits are persisted here — a failed attempt (account-service rejected it before confirming which account/customer it targeted) is fully captured in the structured audit log (`com.banksphere.employee.AUDIT`) instead, which is why `status`/`failure_reason` don't currently need a populated `FAILED` case.

**Indexes:** `idx_cash_deposit_operations_branch_created_at` on `(branch_id, created_at DESC)` — the "my branch's recent deposits" history query's access path.

Migrations: `V1__create_branch_and_employee_tables.sql` (schema), `V2__seed_bootstrap_branch_and_admin.sql` (one seeded branch + one seeded `ADMIN` employee, closing the "nothing can create the first admin" bootstrap problem — see ADR-006, Decision 6), `V3__create_cash_deposit_operations_table.sql` (Phase 9B, above).

## Branch-scoped authorization uses `accounts.ifsc`, not a new field (Phase 9B)

Employee cash deposits (see `docs/architecture/employee-operations.md`) needed a way to check "is this account within the depositing teller's own branch" — rather than inventing a new `branch_id` column on `accounts` (a table `account-service` owns and `employee-service` must never reach into directly), the existing `accounts.ifsc` column (added Phase 8A) and `branches.ifsc` (added Phase 9A) are compared instead: the same relationship a real IFSC encodes in an actual bank. This is enforced in application code (`AccountServiceImpl.employeeDeposit`, using an IFSC value carried on the employee's own JWT), not a database constraint — see [ADR-007, Decision 6](../architecture/decisions/ADR-007-branch-cash-deposit.md#decision-6--branch-authorization-derived-from-accountifsc-not-an-invented-field).

## `banksphere_kyc` (kyc-service) — Phase 9C

The sixth database, following the same database-per-service model — no shared module, no shared database, no cross-database foreign keys. See [ADR-008](../architecture/decisions/ADR-008-kyc-domain-and-document-storage.md) and [docs/architecture/customer-360-and-kyc.md](../architecture/customer-360-and-kyc.md).

### `kyc_applications`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `customer_id` | `UUID` | Not null — references a customer by id; not a DB-level FK (separate database). Partial unique index (`WHERE status NOT IN ('APPROVED','REJECTED')`) enforces at most one non-terminal application per customer. |
| `status` | `VARCHAR(40)` | `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `ADDITIONAL_INFORMATION_REQUIRED`, `RESUBMITTED`, `APPROVED`, `REJECTED` |
| `pan_number` | `VARCHAR(20)` | Illustrative demo field |
| `occupation` | `VARCHAR(100)` | Illustrative demo field |
| `annual_income_range` | `VARCHAR(30)` | Illustrative demo field |
| `current_reviewer_id` | `UUID` | Nullable — informational only ("who's actively looking at this"), not a pessimistic lock; references `employees.id` by value |
| `submitted_at` | `TIMESTAMPTZ` | Nullable |
| `reviewed_at` | `TIMESTAMPTZ` | Nullable — set only on the final `APPROVED`/`REJECTED` decision |
| `reviewed_by` | `UUID` | Nullable — the employee who made the final decision; references `employees.id` by value |
| `review_reason` | `VARCHAR(1000)` | Nullable — always customer-visible (a rejection reason or an additional-information request), never internal-only text |
| `version` | `BIGINT` | Optimistic-locking column — identical mechanism to `accounts.version` since Phase 7A |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | |

### `kyc_documents`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `kyc_application_id` | `UUID` | Not null, FK to `kyc_applications.id` (same database — a real FK) |
| `document_type` | `VARCHAR(30)` | `PAN`, `IDENTITY_PROOF`, `ADDRESS_PROOF`, `BANK_STATEMENT` — not a claim of legal completeness |
| `document_status` | `VARCHAR(20)` | `PENDING`, `VERIFIED`, `REJECTED` |
| `storage_reference` | `VARCHAR(255)` | Not null — an opaque key into `DocumentStorage`, never a public URL, never returned in any customer- or employee-facing response |
| `original_file_name` | `VARCHAR(255)` | Not null — metadata only, never used to address the file on disk (see `LocalDocumentStorage`) |
| `content_type` | `VARCHAR(100)` | Not null |
| `file_size` | `BIGINT` | Not null |
| `submitted_at` | `TIMESTAMPTZ` | |
| `verified_at` / `verified_by` | `TIMESTAMPTZ` / `UUID` | Nullable — set when an employee verifies or rejects the document |
| `rejection_reason` | `VARCHAR(500)` | Nullable |

Duplicate-upload prevention (a non-`REJECTED` document of the same type already exists) is enforced in the service layer, not a database constraint — a `REJECTED` document must remain re-uploadable under the same type without deleting the row first, which isn't expressible as a single static partial-index predicate.

### `kyc_status_history`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key |
| `kyc_application_id` | `UUID` | Not null, FK to `kyc_applications.id` |
| `from_status` | `VARCHAR(40)` | Nullable |
| `to_status` | `VARCHAR(40)` | Not null |
| `changed_by_employee_id` | `UUID` | Nullable — null for a customer-initiated transition (submit/resubmit), never fabricated |
| `reason` | `VARCHAR(1000)` | Nullable |
| `changed_at` | `TIMESTAMPTZ` | |

A real, queryable transition log for the review screen's "review history" panel — distinct from `KycAuditLog`'s free-form structured log lines (which are for a future log shipper/Audit Service, not rendered directly in any UI).

Migrations: `V1__create_kyc_applications_table.sql`, `V2__create_kyc_documents_table.sql`, `V3__create_kyc_status_history_table.sql`.

## Why no cross-database foreign keys

`accounts.customer_id`, `transactions.account_id`, `beneficiaries.customer_id`, and `kyc_applications.customer_id` are not enforced by a database foreign key because each service has its own database — this is a deliberate consequence of the database-per-service model, not an oversight. Referential integrity across services is a known Phase 1 gap; see [application-architecture.md](../architecture/application-architecture.md) for the related discussion on the account→transaction recording call. `employees.branch_id` is the one FK in the whole system's data model that *is* enforced at the database level, since `employee-service` is the sole owner of both `employees` and `branches` — the general "no cross-database FK" rule applies specifically to references that cross a service boundary, not to every foreign key everywhere. `kyc_documents.kyc_application_id`/`kyc_status_history.kyc_application_id` are likewise real, DB-enforced FKs, since `kyc-service` is the sole owner of all three of its own tables.

## Timestamps

All timestamps are stored as `TIMESTAMPTZ` (UTC) and each service forces its JVM default timezone to UTC at startup (`config/TimeZoneConfig`), independent of host locale.
