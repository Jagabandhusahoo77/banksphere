# Networking (AWS DEV/TEST, Phase 10A — k3s rework)

```text
Internet
  │
  ├── api-<env>.<domain>  (or the ALB's own DNS name if no domain yet)
  │     │  HTTPS (443, ACM cert once a domain exists — else plain HTTP)
  │     ▼
  │   ALB  (public subnets, security group open to the internet on 80/443 only, ONE target group)
  │     │  HTTP, only from the ALB's own security group
  │     ▼
  │   EC2 instance  (public subnet, security group open ONLY to the ALB's security group, on :80)
  │     └── k3s node → Traefik (bundled ingress controller) → Kubernetes Services, by path — see docs/deployment/ingress.md
  │            ├── /actuator/health                          → customer-service   :8081  (ALB health check target)
  │            ├── /api/v1/auth/*, /api/v1/customers/*        → customer-service   :8081
  │            ├── /api/v1/accounts/*                         → account-service    :8082
  │            ├── /api/v1/transactions/*                     → transaction-service:8083
  │            ├── /api/v1/beneficiaries/*                    → beneficiary-service:8084
  │            ├── /api/v1/employees/*, /api/v1/operations/*, /api/v1/employee/* → employee-service:8085
  │            └── /api/v1/kyc/*                              → kyc-service        :8086
  │
  ├── app-<env>.<domain>  → CloudFront → S3 (customer-portal static build) — see docs/deployment/frontend-hosting.md
  └── ops-<env>.<domain>  → CloudFront → S3 (employee-portal static build) — see docs/deployment/frontend-hosting.md
```

This is a materially different shape from the phase's first draft (Docker Compose + an nginx gateway container on the EC2 instance, three ALB target groups for app/ops/api) — see the Phase 10A architecture-correction report for why. The two biggest changes:

1. **Backend routing moved from a hand-rolled nginx gateway container to Traefik**, k3s's bundled ingress controller, driven by a Kubernetes `Ingress` resource the Helm chart creates (`gitops/apps/banksphere/templates/ingress.yaml`) instead of a `gateway.nginx.conf` file baked into a Docker image. The path-prefix routing table itself is unchanged — same six prefixes, same target services, same "no backend route was renamed" property.
2. **Frontends moved off the EC2 instance entirely**, to S3 + CloudFront (see `docs/deployment/frontend-hosting.md`). The ALB now has exactly one target (the k3s node's Traefik ingress) instead of three, and no longer needs host-header/path-priority listener rules to make relative frontend API calls work — CloudFront and the ALB are two independent, non-overlapping traffic paths, not one ALB juggling three origins.

Postgres and the six backend services still publish **no host port at all** on the EC2 instance's own network interface — they're only reachable as Kubernetes Services (ClusterIP, ie. cluster-internal), the direct Kubernetes equivalent of "no port published on the Docker bridge network" from the original design.

## Why Traefik, not a separate ingress-nginx install

k3s ships with Traefik pre-installed and pre-configured as its default ingress controller — using it costs nothing extra to install or maintain, versus deploying and operating a second ingress controller (e.g. ingress-nginx) that k3s doesn't already provide. For a single-node DEV/TEST cluster, Traefik's feature set (host/path routing, TLS passthrough if ever needed) is more than sufficient. See `infrastructure/terraform/modules/k3s`.

## Why the ALB now has only one target group

Backend traffic (`api-<env>.<domain>`) is the only thing still routed through the ALB → EC2 → k3s path. Frontend traffic no longer touches the ALB or the EC2 instance at all — see `docs/deployment/frontend-hosting.md`. This is what let `infrastructure/terraform/modules/alb` shrink from three target groups + host/path listener rules down to one target group and a plain default action (see that module's own comment).

## Why the frontends now build with a real, cross-origin API URL (not empty/relative)

`frontend`/`employee-portal` are Vite apps — `VITE_*_SERVICE_URL` is baked in at **build** time, not read at runtime. The pre-rework pipeline built every frontend artifact with every `VITE_*_SERVICE_URL` build-arg set to an **empty string**, relying on the frontend being served from the *same* ALB/origin as the API (via a path-priority listener rule) so a relative URL still reached the right place.

That assumption breaks under this rework: `app-<env>.<domain>` is now served entirely by CloudFront/S3 (see `docs/deployment/frontend-hosting.md`) — a genuinely different origin from `api-<env>.<domain>` (the ALB). An empty/relative `VITE_*_SERVICE_URL` there would resolve against `app-<env>.<domain>` itself, which has no `/api/v1/...` object — S3/CloudFront would either 404 or (worse, given the SPA `custom_error_response` rewrite) silently return `index.html`.

**Fixed accordingly, not left as a latent bug**: `azure-pipelines.yml`'s `DeployFrontendsDev`/`DeployFrontendsTest` stages build the static `dist/` output **separately per environment**, with `VITE_*_SERVICE_URL` set to the real `https://api-<env>.<domain>` (or the ALB's own DNS name, pre-domain). This is a deliberate, narrow exception to "build once, promote the same artifact" — the six backend Docker images are still built exactly once — because a static frontend has no runtime env-injection mechanism and DEV/TEST are genuinely different API origins. `CORS_ALLOWED_ORIGINS` (set per environment in `gitops/apps/banksphere/values-<env>.yaml`) is the other half of making this cross-origin call work — both are currently `TODO`-marked placeholders pending a real domain, same as every other environment-specific value in this phase (see `docs/deployment/dns-and-https.md`).

The container images pushed to ECR for `customer-portal`/`employee-portal` (still built once, still with empty build-args) are a separate, narrower artifact — used only by `gitops/apps/banksphere`'s disabled-by-default in-cluster portal Deployments, a genuinely same-origin scenario (reached through the same Traefik Ingress as the backend) where relative URLs are correct. See `docs/deployment/helm.md`.

## No NAT Gateway

Unchanged. The EC2 instance sits in a **public** subnet with an Internet Gateway route. See `infrastructure/terraform/modules/networking`'s own comment and `docs/deployment/cost-drivers.md`.

## SSH is closed by default

Unchanged. `enable_ssh = false` by default; access via **AWS Systems Manager Session Manager**. See `infrastructure/terraform/modules/ec2`/`modules/security`.
