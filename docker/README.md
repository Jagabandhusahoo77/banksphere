# Docker (Local Development)

Local Docker Compose environment for running BankSphere on a developer machine, without Kubernetes.

## Structure

```text
docker/
├── local/     docker-compose.yml orchestrating the local stack + .env.example
├── postgres/  Local Postgres init scripts (creates the per-service databases)
├── redis/     Local Redis config — not used yet (Phase 7+)
└── kafka/     Local Kafka config — not used yet (Phase 7+)
```

## Current status (as of Phase 9D)

`docker/local/docker-compose.yml` brings up the full six-service backend plus both frontends:

```text
postgres              PostgreSQL 16, 6 databases (customer/account/transaction/beneficiary/employee/kyc)
customer-service       Spring Boot, :8081 — built from backend/services/customer-service/Dockerfile
account-service        Spring Boot, :8082 — built from backend/services/account-service/Dockerfile
transaction-service    Spring Boot, :8083 — built from backend/services/transaction-service/Dockerfile
beneficiary-service    Spring Boot, :8084 — built from backend/services/beneficiary-service/Dockerfile
employee-service       Spring Boot, :8085 — built from backend/services/employee-service/Dockerfile
kyc-service            Spring Boot, :8086 — built from backend/services/kyc-service/Dockerfile, persists uploaded documents to the banksphere-kyc-documents named volume
frontend               Customer portal, React SPA served by nginx, built from frontend/Dockerfile — host port 5173
employee-portal        Employee portal, React SPA served by nginx, built from employee-portal/Dockerfile — host port 5174
```

`customer-service`/`account-service`/`transaction-service`/`beneficiary-service`/`kyc-service` were Phase 1/6/9C additions to this file; `employee-service` and `employee-portal` (Phase 9A) are the only two services with a genuinely separate JWT signing secret (`EMPLOYEE_JWT_SECRET`) and CORS origin from the rest — see [ADR-006](../docs/architecture/decisions/ADR-006-employee-identity-and-rbac.md). See [docs/architecture/microservices.md](../docs/architecture/microservices.md) for the full current architecture and [backend/README.md](../backend/README.md) for the service table.

Redis and Kafka are **not** part of any phase implemented so far (see the root README's development phases) — `docker/redis/` and `docker/kafka/` remain placeholders.

**Cloud (AWS DEV/TEST) deployment**, as of the Phase 10A k3s/GitOps rework, runs on **Kubernetes (k3s) via Helm + Argo CD**, not Docker Compose at all — see [`infrastructure/README.md`](../infrastructure/README.md), [`gitops/README.md`](../gitops/README.md), and [`docs/deployment/k3s.md`](../docs/deployment/k3s.md). This file (`docker/local/`) remains the local-development-only stack, entirely unaffected by that: it builds images from source, publishes every backend service's port directly to the host, and has no reverse proxy, TLS, Kubernetes, or AWS dependency of any kind.

`infrastructure/docker/{dev,test}/` (an earlier Phase 10A design that ran Docker Compose directly on the cloud EC2 instance, since superseded by the k3s rework) still exists on disk as a **reference/local-fallback artifact only** — it is not deployed to DEV/TEST by anything in this repository anymore. See `infrastructure/README.md`'s own note on this.

## Running the stack

```bash
cd docker/local
cp .env.example .env    # optional — defaults work out of the box for local dev
docker compose up --build
```

Then open `http://localhost:5173` (customer portal) or `http://localhost:5174` (employee portal), or your own `FRONTEND_PORT`/`EMPLOYEE_PORTAL_PORT`, in a browser.

To stop and remove containers (keeping the Postgres data volume and KYC documents volume):

```bash
docker compose down
```

To also wipe the Postgres data volume and KYC documents volume:

```bash
docker compose down -v
```

> **Note:** this Compose file targets the standard `docker compose` (v2, bundled as a Docker CLI plugin) or `docker-compose` (v1) tooling. If neither is available, each image can still be built and run individually with `docker build` / `docker run` — see [`backend/README.md`](../backend/README.md) and [`frontend/README.md`](../frontend/README.md).

## Credentials

`docker/local/.env.example` documents the local-only default database and JWT credentials (`banksphere` / `banksphere_local_dev`, and the two local-dev-only JWT signing secrets). These are for local development exclusively — never reuse them anywhere else, and never commit a real `.env` file (already covered by the repo-root `.gitignore`).

## Network

All services join a single Docker bridge network (`banksphere-network`) defined in `docker-compose.yml`, so services can reach each other by container/service name (e.g. account-service reaches transaction-service at `http://transaction-service:8083`, and as of Phase 9D also reaches customer-service at `http://customer-service:8081` to confirm a step-up challenge). The two frontends, however, run entirely in the browser, so each reaches the backend services it needs via their **published host ports** (`localhost:8081`–`localhost:8086`), not the internal Docker network.
