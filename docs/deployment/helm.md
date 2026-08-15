# Helm chart — `gitops/apps/banksphere` (Phase 10A rework)

## Structure

```text
gitops/apps/banksphere/
├── Chart.yaml
├── values.yaml          Chart-wide defaults — ports, health paths, DB names, service-to-service URLs
├── values-dev.yaml       DEV overrides — image registry, ingress host, CORS origins (currently TODO placeholders)
├── values-test.yaml      TEST overrides — same shape as DEV
└── templates/
    ├── _helpers.tpl              banksphere.labels, banksphere.image
    ├── postgres-configmap.yaml   the database-per-service init script, generated from values.yaml
    ├── postgres-pvc.yaml / postgres-deployment.yaml / postgres-service.yaml
    ├── backend-deployment.yaml / backend-service.yaml   one range over .Values.services — all 6 backend services share this template
    ├── backend-pvc.yaml           only kyc-service uses this (persistence.enabled)
    ├── ingress.yaml                see docs/deployment/ingress.md
    ├── portal-deployment.yaml / portal-service.yaml   disabled by default — see "Portals" below
    └── NOTES.txt
```

Never `helm install`ed by a human in DEV/TEST — Argo CD renders and applies it on every reconcile (see `docs/deployment/gitops.md`).

## Why one template per resource TYPE, not one per service

All six backend services are the same shape: one Spring Boot container, `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` plus zero or more of `JWT_SECRET`/`EMPLOYEE_JWT_SECRET` (from a shared Kubernetes Secret), `GET /actuator/health` for both liveness and readiness. `templates/backend-deployment.yaml` is a single `range` over `.Values.services` (a map) rather than six near-identical template files — adding a 7th service is a `values.yaml` entry, not a new template. Each service's `secretEnv` list (e.g. `[JWT_SECRET, EMPLOYEE_JWT_SECRET]`) mirrors `docker/local/docker-compose.yml`'s own per-service env var list exactly — `employee-service`, for instance, only ever gets `EMPLOYEE_JWT_SECRET` injected, never `JWT_SECRET`, matching its actual Spring configuration.

## Service naming mirrors docker-compose on purpose

Kubernetes Service names in this chart (`postgres`, `customer-service`, `account-service`, ...) are deliberately **identical** to `docker/local/docker-compose.yml`'s own container names — so an env var value like `CUSTOMER_SERVICE_URL: http://customer-service:8081` is **byte-for-byte the same string** whether the target is local Docker Compose or this chart. No per-environment hostname translation to get wrong.

## Image naming

`values.yaml`'s `image.repositoryPrefix` (`banksphere`) + each service's map key (e.g. `customer-service`) combine to `banksphere/customer-service` — matching `infrastructure/terraform/modules/ecr`'s actual repository naming (`"${project_name}/${service_name}"`) exactly. `image.registry` is a per-environment `values-<env>.yaml` override (the ECR registry hostname, a Terraform output not known at chart-authoring time — see that file's own `TODO` comment) and `image.tag` is what the CI pipeline's GitOps commit updates on every deploy (see `docs/deployment/gitops.md`).

## Secrets — referenced, never created

The chart never creates a Kubernetes `Secret` — `appSecretName` (default `banksphere-app-secrets`) and `imagePullSecrets.name` (default `ecr-registry-credentials`) are **references** to Secrets populated by `infrastructure/terraform/modules/k3s`'s bootstrap script (one-time for app secrets, refreshed every few hours for the ECR pull credential — see `docs/deployment/k3s.md`). The two names must match on both sides; they're wired as matching defaults, not hardcoded independently in two places.

## Postgres: a Deployment, not a StatefulSet

Single-node cluster, single `ReadWriteOnce` PVC — there is never more than one Postgres Pod, so StatefulSet's stable-identity/ordered-scaling guarantees buy nothing here. `strategy: { type: Recreate }` is required specifically because the PVC is RWO: a `RollingUpdate` would try to schedule the replacement Pod before the old one released the volume and get stuck waiting forever. Storage is `local-path` (k3s's bundled dynamic provisioner, backed by the node's own root EBS volume) — see `docs/deployment/postgresql.md`.

## KYC document storage

`kyc-service` is the only backend service with `persistence.enabled: true` in `values.yaml` — a dedicated PVC (`kyc-service-data`, also `local-path`-provisioned) mounted at `/data/kyc-documents`, matching `KYC_DOCUMENT_STORAGE_PATH` and the local Docker Compose volume's own mount point exactly. See `docs/deployment/kyc-storage.md`.

## Portals: disabled-by-default reference templates

`values.yaml`'s `portals.customerPortal`/`portals.employeePortal` both default to `enabled: false` — the real DEV/TEST serving path is S3 + CloudFront (see `docs/deployment/frontend-hosting.md`), not this chart. The Deployment/Service templates exist anyway, as an honest, working reference implementation (verified: `helm template ... --set portals.customerPortal.enabled=true` renders and passes `kubeconform`) — matching `infrastructure/docker/{dev,test}`'s own "reference/local-fallback only" status (see that directory's README) rather than leaving an untested, aspirational template in the chart.

## Validation performed this phase

`helm lint` and `helm template` (against both `values-dev.yaml` and `values-test.yaml`, plus a variant with both portals force-enabled) all pass cleanly, and every rendered manifest set validates against the Kubernetes API schema via `kubeconform -ignore-missing-schemas` (the `-ignore-missing-schemas` flag skips the one CRD-adjacent case — none, actually needed here, since this chart emits only core/apps/networking API objects; kept for robustness). **Not performed**: an actual `helm install`/`kubectl apply` against a real cluster — no k3s cluster exists to test against this phase (no AWS resources were provisioned — see the Phase 10A report). This is stated explicitly rather than implied.

## Known gaps, not silently hidden

- **`image.registry`, `ingress.host`, and every `CORS_ALLOWED_ORIGINS`-family value are `TODO`-marked placeholders** in both `values-dev.yaml`/`values-test.yaml` — genuinely unknown until `terraform apply` actually runs and/or a domain is registered. See those files' own header comments.
- **No `values-prod.yaml`** — PROD isn't registered by any Argo CD `Application`; adding one without the EKS/RDS design work behind it would imply a deployment path that doesn't exist. See `gitops/environments/prod/README.md`.
- **No HorizontalPodAutoscaler** — a single-node cluster with `replicas: 1` everywhere has nowhere to scale to; out of scope for this phase's single-node design.
