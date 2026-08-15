# Backend

BankSphere's backend is a **microservices architecture** built with Java and Spring Boot. Each service under [`services/`](services/) is an independently deployable, independently scalable Spring Boot application with its own build, Dockerfile, and data ownership.

## Structure

```text
backend/
├── services/     Independent microservices (see below)
├── shared/       Libraries shared across services (not yet used — see note below)
├── database/     Cross-service migration/seed conventions (not yet used)
└── kafka/        Kafka topic and schema definitions (Phase 7+)
```

## Services

| Service | Responsibility | Status |
|---|---|---|
| `customer-service` | Customer identity and profile data | **Implemented** (Phase 1) |
| `account-service` | Bank account creation, balances, deposits/withdrawals | **Implemented** (Phase 1) |
| `transaction-service` | Transaction ledger and history | **Implemented** (Phase 1) |
| `api-gateway` | Single entry point; routing, auth enforcement, rate limiting | Not yet implemented |
| `auth-service` | Authentication, authorization, token issuance | Not yet implemented |
| `payment-service` | Fund transfers and bill payments | Not yet implemented |
| `beneficiary-service` | Beneficiary/payee management | **Implemented** (Phase 6) |
| `employee-service` | Employee identity + RBAC for the internal bank-operations portal — a new service outside this original list, backing a second (employee) channel, not a new customer-facing capability. As of Phase 9B, also the "Employee Operations API" for the first real employee-initiated banking operation (branch cash deposit); as of Phase 9C, also the Customer 360 cross-service aggregation | **Implemented** (Phase 9A identity/RBAC, Phase 9B operations, Phase 9C Customer 360) — see [docs/architecture/employee-platform.md](../docs/architecture/employee-platform.md) / [employee-operations.md](../docs/architecture/employee-operations.md) |
| `kyc-service` | KYC application lifecycle, document upload/verification, and employee review workflow — a genuine bounded domain (own database, own document storage), not a sub-module of employee-service; called directly by both portals | **Implemented** (Phase 9C) — see [docs/architecture/customer-360-and-kyc.md](../docs/architecture/customer-360-and-kyc.md) |
| `card-service` | Debit/credit card issuance and management | Not yet implemented |
| `loan-service` | Loan applications and servicing | Not yet implemented |
| `notification-service` | Email/SMS/push notifications | Not yet implemented |
| `audit-service` | Audit trail / compliance logging | Not yet implemented |

**As of Phase 10A**, the six implemented services above (customer/account/transaction/beneficiary/employee/kyc) are the actual deployable unit for AWS DEV/TEST infrastructure — see [`infrastructure/README.md`](../infrastructure/README.md). `api-gateway`/`auth-service`/`payment-service`/`card-service`/`loan-service`/`notification-service`/`audit-service` remain empty scaffold directories only.

_This table (and the endpoint/environment-variable tables below it) has historically lagged actual implementation — `beneficiary-service` and account-service's transfer endpoint were both added without a corresponding update here until this phase touched the table directly to add `employee-service`. The authoritative, kept-current reference for endpoints is [docs/api/README.md](../docs/api/README.md); for architecture, [docs/architecture/](../docs/architecture/)._

**A note specific to `employee-service`:** unlike every other service in this table, it does **not** share `JWT_SECRET`/`CORS_ALLOWED_ORIGINS` defaults with the rest — it reads its own `EMPLOYEE_JWT_SECRET` and `EMPLOYEE_PORTAL_CORS_ORIGIN`, and is called by a separate frontend (`employee-portal/`, port 5174) rather than `frontend/`. See [ADR-006](../docs/architecture/decisions/ADR-006-employee-identity-and-rbac.md) for why.

**Phase 9B addition:** `EMPLOYEE_JWT_SECRET` is now also read by `customer-service`, `account-service`, and `transaction-service` — not to issue employee tokens (only `employee-service` does that), but so each can independently validate one on the small set of new employee-only endpoints each gained (`GET /customers/employee-lookup/{id}`; `GET /accounts/employee-lookup[...]` + `POST /accounts/{id}/employee-deposit`; and `POST /transactions`, which already accepted any valid JWT). `employee-service` itself gained `ACCOUNT_SERVICE_URL`/`CUSTOMER_SERVICE_URL` to call those endpoints server-to-server. See [ADR-007](../docs/architecture/decisions/ADR-007-branch-cash-deposit.md).

Each implemented service follows the same structure:

```text
service-name/
├── src/main/java/com/banksphere/<service>/
│   ├── controller/    REST controllers (no business logic)
│   ├── service/       Business logic
│   ├── repository/    Spring Data JPA repositories
│   ├── entity/         JPA entities
│   ├── dto/            Request/response DTOs (never expose entities directly)
│   ├── exception/       Custom exceptions + global exception handler
│   └── config/          Spring configuration
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    Flyway versioned migrations
├── src/test/java/...     JUnit 5 + Mockito unit/controller tests
├── pom.xml
└── Dockerfile             Multi-stage: maven build → eclipse-temurin JRE runtime
```

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+ (or use the Docker-based build below if you don't want to install Java/Maven locally)
- Docker (for PostgreSQL and/or building service images)

If you don't have Java/Maven installed locally, you can build and test any service using the official Maven image, e.g.:

```bash
docker run --rm -v "$PWD":/build -w /build maven:3.9-eclipse-temurin-21 mvn -B verify
```

run from inside the service's directory (`backend/services/customer-service`, etc).

## Running PostgreSQL locally

The simplest option is the shared Compose file, which starts Postgres and creates all six databases automatically:

```bash
cd docker/local
docker compose up postgres
```

This uses `docker/postgres/init/001-create-databases.sh` to create `banksphere_account`, `banksphere_transaction`, `banksphere_beneficiary`, `banksphere_employee`, and `banksphere_kyc` alongside the default `banksphere_customer` database (created via Postgres's own `POSTGRES_DB` bootstrap — see [docs/database/README.md](../docs/database/README.md)).

To run Postgres standalone instead:

```bash
docker run --name banksphere-postgres \
  -e POSTGRES_USER=banksphere \
  -e POSTGRES_PASSWORD=banksphere_local_dev \
  -e POSTGRES_DB=banksphere_customer \
  -p 5432:5432 \
  -v "$(pwd)/../docker/postgres/init":/docker-entrypoint-initdb.d:ro \
  -d postgres:16-alpine
```

## Running a service locally

Each service reads its database connection and (for account-service) the transaction-service URL from environment variables — nothing is hard-coded. Defaults assume Postgres is reachable at `localhost:5432` with the credentials above.

```bash
cd backend/services/customer-service
DB_HOST=localhost DB_PORT=5432 DB_NAME=banksphere_customer \
DB_USERNAME=banksphere DB_PASSWORD=banksphere_local_dev \
mvn spring-boot:run
```

Repeat for each of the other five services, setting `DB_NAME`/port per the table below. `account-service` additionally needs `TRANSACTION_SERVICE_URL`/`CUSTOMER_SERVICE_URL` set if not using the defaults (`http://localhost:8083`/`8081`); `employee-service` needs `ACCOUNT_SERVICE_URL`/`CUSTOMER_SERVICE_URL`/`TRANSACTION_SERVICE_URL`/`BENEFICIARY_SERVICE_URL`/`KYC_SERVICE_URL`. See each service's own `application.yml` for the full, current list — it is the source of truth, not this table.

| Service | Default port | Database | Health check |
|---|---|---|---|
| customer-service | 8081 | `banksphere_customer` | `GET /actuator/health` |
| account-service | 8082 | `banksphere_account` | `GET /actuator/health` |
| transaction-service | 8083 | `banksphere_transaction` | `GET /actuator/health` |
| beneficiary-service | 8084 | `banksphere_beneficiary` | `GET /actuator/health` |
| employee-service | 8085 | `banksphere_employee` | `GET /actuator/health` |
| kyc-service | 8086 | `banksphere_kyc` | `GET /actuator/health` |

Flyway runs automatically on startup and creates/updates each service's schema — Hibernate's `ddl-auto` is set to `validate`, so the schema is never auto-generated in any environment.

## Environment variables

The variables below are common to every service; each service also has its own additional set (JWT secrets, downstream service URLs, OTP/step-up tuning, KYC storage path, etc.) that grew across phases — rather than duplicate all of them here (and drift stale again, as this section previously did by only covering the original three Phase 1 services), the authoritative, currently-maintained list is **`docker/local/docker-compose.yml`** (every variable each service actually reads, with its local-dev default) and **`docker/local/.env.example`** (the same variables, documented). Each service's own `application.yml` shows exactly how a variable is used.

| Variable | Used by | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | all | 8081–8086 (service-specific) | HTTP port the service listens on |
| `DB_HOST` | all | `localhost` | PostgreSQL host |
| `DB_PORT` | all | `5432` | PostgreSQL port |
| `DB_NAME` | all | service-specific | Database name (`banksphere_customer`, `banksphere_account`, `banksphere_transaction`, `banksphere_beneficiary`, `banksphere_employee`, `banksphere_kyc`) |
| `DB_USERNAME` | all | `banksphere` | Database user |
| `DB_PASSWORD` | all | `banksphere_local_dev` | Database password — **never commit a real value**; see `docker/local/.env.example` |
| `JWT_SECRET` | customer/account/transaction/beneficiary/kyc-service | local-dev-only default | Customer-principal JWT signing secret (customer-service issues; the others only verify) |
| `EMPLOYEE_JWT_SECRET` | all six | local-dev-only default | Employee-principal JWT signing secret — deliberately separate from `JWT_SECRET`; employee-service issues, the other five only verify (see ADR-006) |
| `CORS_ALLOWED_ORIGINS` | customer/account/transaction/beneficiary-service | `http://localhost:5173` | Comma-separated browser origins allowed on `/api/**` |

## API endpoints

| Service | Port | Base paths |
|---|---|---|
| customer-service | 8081 | `/api/v1/auth/**` (register/login/OTP login/refresh/step-up), `/api/v1/customers/**` |
| account-service | 8082 | `/api/v1/accounts/**` (create/deposit/withdraw/transfer/resolve-recipient) |
| transaction-service | 8083 | `/api/v1/transactions/**` |
| beneficiary-service | 8084 | `/api/v1/beneficiaries/**` |
| employee-service | 8085 | `/api/v1/employees/**`, `/api/v1/operations/**` (cash deposit), `/api/v1/employee/customers/{id}/360` |
| kyc-service | 8086 | `/api/v1/kyc/**` |

This table is intentionally a summary, not a reference — [docs/api/README.md](../docs/api/README.md) is the authoritative, currently-maintained endpoint reference (full request/response shapes, status codes, auth requirements) and is kept in sync with the actual implementation on every phase; this file previously duplicated a stale, Phase-1-only subset of it and has been trimmed to avoid drifting out of sync again.

## Running tests

```bash
cd backend/services/customer-service   # or any of the other five services
mvn test
```

or, without a local JDK/Maven:

```bash
docker run --rm -v "$PWD":/build -w /build maven:3.9-eclipse-temurin-21 mvn -B verify
```

Each service has:
- **Unit tests** (`*ServiceImplTest`) using JUnit 5 + Mockito, covering the service layer against a mocked repository (create/get/update, deposit/withdraw, insufficient balance, invalid amount, not-found).
- **Controller tests** (`*ControllerTest`) using `@WebMvcTest` + MockMvc, covering HTTP status codes and validation without needing a database.

Neither test type requires a running PostgreSQL instance.

## Health checks

Each service exposes Spring Boot Actuator with only `health` and `info` enabled (`management.endpoints.web.exposure.include: health,info`) — no other actuator endpoints are exposed publicly.

```bash
curl http://localhost:8081/actuator/health
```

## Shared

`shared/` holds cross-cutting libraries intended for multiple services (`common-models`, `common-exceptions`, `common-utils`). **Not used yet** — each Phase 1 service is self-contained on purpose, so it can build and deploy independently without a multi-module Maven reactor. This will be revisited if/when duplicated code across services becomes a real maintenance burden.

## Database

`database/migrations` and `database/seed` are placeholders for cross-service conventions. Today each service owns its migrations directly under its own `src/main/resources/db/migration/`.

## Kafka

`kafka/topics` and `kafka/schemas` are placeholders for Phase 7, when asynchronous communication is introduced. Not used in Phase 1.
