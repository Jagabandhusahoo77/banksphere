# Frontend hosting — S3 + CloudFront (Phase 10A — k3s rework)

## What changed

The original Phase 10A design served both frontends (`frontend`, the customer portal, and `employee-portal`) as nginx containers on the same EC2 instance as the backend, behind the ALB. This rework moves them off the EC2 instance/Kubernetes entirely, onto **S3 + CloudFront**:

```text
app-<env>.<domain> → CloudFront (OAC) → S3 bucket (customer-portal static build)
ops-<env>.<domain> → CloudFront (OAC) → S3 bucket (employee-portal static build)
```

## Why S3 + CloudFront instead of a Kubernetes Deployment

Both portals are static single-page apps — `vite build` output is plain HTML/JS/CSS with no server-side logic. Running that behind nginx-in-a-container-in-Kubernetes works, but adds a container, a Deployment, a Service, an Ingress rule, and CPU/memory budget on the single k3s node for something that has no actual runtime behavior. S3 + CloudFront is:
- **Cheaper** at this scale (see `docs/deployment/cost-drivers.md`) — no compute cost for serving static files, only storage (pennies) and a small CDN request cost.
- **Not competing for the k3s node's limited memory** — see `infrastructure/terraform/modules/ec2`'s `instance_type` comment on why that budget is already tight with six Spring Boot JVMs + Postgres + k3s + Argo CD.
- **A better fit for a CDN's actual job** — edge caching, gzip/brotli, TLS termination close to the user — none of which Traefik-on-a-single-node meaningfully provides.

Both Deployment templates still exist in the Helm chart, disabled by default, as a documented reference/fallback capability — see `docs/deployment/helm.md`.

## How a build gets there

Unlike the six backend services (one Docker image, built once, promoted unchanged from DEV to TEST via a GitOps commit — see `docs/deployment/gitops.md`), the two frontends' **static build output** is built **separately per environment**, directly inside `azure-pipelines.yml`'s `DeployFrontendsDev`/`DeployFrontendsTest` stages:

```text
npm ci && npm run build (VITE_*_SERVICE_URL = https://api-<env>.<domain>)
  → aws s3 sync dist/ to the environment's S3 bucket
  → aws cloudfront create-invalidation (evict the CDN cache)
```

This is a deliberate, narrow exception to "build once, promote the same artifact," specific to these two frontends — see `docs/deployment/networking.md`'s "Why the frontends now build with a real, cross-origin API URL" section for the full reasoning (Vite bakes config at build time, and `app-<env>.<domain>`/`api-<env>.<domain>` are different origins, so DEV and TEST genuinely need two different static builds).

## S3 bucket design (`infrastructure/terraform/modules/s3`)

One private bucket per portal per environment (`banksphere-dev-customer-portal-<account-id>`, etc. — the account ID suffix is required for global S3 bucket-name uniqueness):
- **Never publicly readable directly** — `aws_s3_bucket_public_access_block` blocks all four public-access vectors; the only reader is CloudFront, via Origin Access Control (see below).
- Versioned (`aws_s3_bucket_versioning`), so a bad deploy can be rolled back to the previous build by restoring the previous object versions.
- A lifecycle rule expires noncurrent versions after `noncurrent_version_expiration_days` (default 30) and aborts incomplete multipart uploads after 7 days — pure storage-cost hygiene.
- AES256 server-side encryption (no customer-managed KMS key — see `docs/deployment/cost-drivers.md`'s KMS note).
- `BucketOwnerEnforced` ownership controls — ACLs are disabled entirely; access is via bucket policy only.

## CloudFront design (`infrastructure/terraform/modules/cloudfront`)

- **Origin Access Control (OAC)**, not the older/deprecated Origin Access Identity — a signed-request mechanism that lets CloudFront read a fully private S3 bucket, enforced by an S3 bucket policy scoped to that specific distribution's ARN (`AWS:SourceArn` condition) — created in `modules/cloudfront`, not `modules/s3`, specifically to avoid a circular module dependency (the policy needs the distribution's own ARN, which doesn't exist until the distribution does).
- **SPA routing**: `custom_error_response` rewrites both 403 (S3's response for "no such key," since a private bucket returns 403 rather than 404 for OAC-authenticated requests to a missing key) and 404 to a 200 response serving `/index.html` — otherwise a client-side-routed deep link (e.g. `/accounts/123`) would break on refresh, since no such S3 object exists.
- **`PriceClass_100`** (US/Canada/Europe edge locations only) — the cheapest tier that still gives real CDN benefit; see `docs/deployment/cost-drivers.md`.
- **HTTPS**: a `dynamic "viewer_certificate"` block picks between the real ACM certificate (once `certificate_arn` and the site's `alias` are both set) and CloudFront's own default certificate (reachable at the distribution's own `*.cloudfront.net` name) — an empty/unconfigured domain is a fully supported state, same pattern as `modules/dns`'s `domain_configured` gate.
- **ACM certificate region**: CloudFront has a hard requirement that its certificate live in `us-east-1`, regardless of which region the rest of the infrastructure deploys to — see `docs/deployment/dns-and-https.md` and `environments/dev/providers.tf`'s aliased `us_east_1` provider.

## What does NOT change

- The backend API (`api-<env>.<domain>`) is unaffected — still ALB → k3s node → Traefik → Kubernetes Services, see `docs/deployment/ingress.md`.
- `CORS_ALLOWED_ORIGINS`/`EMPLOYEE_PORTAL_CORS_ORIGIN`/`KYC_CORS_ALLOWED_ORIGINS` (set in `gitops/apps/banksphere/values-<env>.yaml`) are what let a browser on `app-<env>.<domain>`/`ops-<env>.<domain>` make a cross-origin call to `api-<env>.<domain>` at all — this is now load-bearing in a way it wasn't in the original same-origin design.
