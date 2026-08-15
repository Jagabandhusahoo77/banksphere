# DNS and HTTPS (AWS DEV/TEST, Phase 10A — k3s rework)

## Current state: no domain configured

Inspection (see the Phase 10A report) found no real registered domain referenced anywhere in this repository — nothing in `docker-compose.yml`, any `.env.example`, or any doc names one. Per the task's own instruction ("do not invent the actual domain... document the required next step rather than fabricating it"), **no placeholder domain was written into any Terraform file**. Every DNS/ACM resource in `infrastructure/terraform/modules/dns`/`modules/acm` is conditional on `var.domain_name` being a non-empty string.

## Two certificates now, not one — a change from the original design

The original (pre-rework) design put everything behind one ALB, so it needed exactly one ACM certificate, in the same region as everything else. This rework splits traffic across two AWS-managed HTTPS endpoints — the ALB (for `api-<env>.<domain>`) and CloudFront (for `app-<env>.<domain>`/`ops-<env>.<domain>`) — see `docs/deployment/frontend-hosting.md` — and **CloudFront has a hard requirement that its certificate live in `us-east-1`**, regardless of which region the rest of the infrastructure deploys to. So each environment now requests **two** ACM certificates (`infrastructure/terraform/modules/acm`, instantiated twice per environment with two different provider configurations — see `environments/dev/providers.tf`'s aliased `us_east_1` provider and `environments/dev/acm.tf`):

| Certificate | Region | Covers | Attached to |
|---|---|---|---|
| `acm_alb` | same as `aws_region` | `api-<env>.<domain>` | the ALB (see `modules/alb`) |
| `acm_cloudfront` | `us-east-1` (always) | `app-<env>.<domain>` + `ops-<env>.<domain>` (one cert, two SANs) | both CloudFront distributions (see `modules/cloudfront`) |

## What happens with no domain (today's actual state)

Each environment still deploys completely — `terraform apply` creates a real ALB, EC2 instance, both CloudFront distributions, both S3 buckets, etc. — but:
- The ALB serves plain **HTTP** on its own AWS-generated DNS name — no `acm_alb` certificate exists yet to attach to an HTTPS listener, so none is created (see `modules/alb`'s `local.https_enabled` gate).
- Both CloudFront distributions serve over their own default `*.cloudfront.net` certificate — no `acm_cloudfront` certificate exists yet, so each distribution's `viewer_certificate` block falls back to `cloudfront_default_certificate = true` (see `modules/cloudfront`'s `dynamic "viewer_certificate"`).
- `app-<env>.<domain>`/`ops-<env>.<domain>`/`api-<env>.<domain>` are not real, resolvable hostnames — nothing points at them, because no domain exists to create a record under. The environments are still reachable via the ALB's own DNS name and each CloudFront distribution's own `*.cloudfront.net` name (see `terraform output`).
- Every `CORS_ALLOWED_ORIGINS`-family value in `gitops/apps/banksphere/values-<env>.yaml` is a `TODO`-marked empty string — a browser page served from a `*.cloudfront.net` origin calling `api-<env>.<domain>` (also not real yet) would be rejected by the backend's own CORS policy either way. This is intentional: an honest, safe failure mode (nothing works cross-origin from a browser until a real domain exists) rather than a silently-wrong wildcard CORS policy.

## What you need to do once you register a domain

1. **Register a domain** (Route53, or any registrar) — not something Terraform can do for you.
2. Decide where DNS is hosted:
   - **Already in Route53**: set `route53_zone_id` in `terraform.tfvars` to the existing zone's ID.
   - **Not in Route53 yet, want Terraform to create the zone**: set `create_hosted_zone = true`. After `terraform apply`, read the `hosted_zone_name_servers` output and update your registrar's NS records to match — **this one step Terraform cannot do for you**, since it requires action at your registrar, not AWS.
   - **DNS hosted elsewhere (e.g. Cloudflare) and staying there**: leave `route53_zone_id` empty and `create_hosted_zone = false`. `terraform apply` will then skip creating any Route53 records — read the `fqdns` output for the three hostnames it expects, and both ACM certificates' DNS validation records (from the AWS Console, since Terraform won't manage them for you in this configuration) to create manually at your DNS provider.
3. Set `domain_name = "your-real-domain.com"` in `terraform.tfvars` (both DEV and TEST — using the **same** domain, with the `-dev`/`-test` prefixes already baked into how hostnames are built; do not set `create_hosted_zone = true` in both if they share one domain — only whichever environment creates the zone first should).
4. `terraform apply` again. This time: both ACM certificates are requested (DNS-validated), the ALB's HTTPS listener activates (HTTP switches to redirect), both CloudFront distributions pick up their real certificate + alias, and (if Terraform manages the zone) all three ALIAS records are created — `api-<env>` → the ALB, `app-<env>`/`ops-<env>` → their respective CloudFront distributions.
5. **Also update `gitops/apps/banksphere/values-<env>.yaml`** — `ingress.host` and every `CORS_ALLOWED_ORIGINS`-family `TODO` — and **the pipeline's `DOMAIN_NAME` variable** (used by `azure-pipelines.yml`'s frontend build steps to bake the real `api-<env>.<domain>` URL into each portal's static build — see `docs/deployment/frontend-hosting.md`). Terraform applying successfully does not, by itself, make the application aware of the new domain — these are two separate systems (infrastructure vs. application config), deliberately not auto-synced this phase.

## Why ACM + ALB/CloudFront, not a certificate installed directly on the EC2 instance

ACM-issued certificates cannot be exported — their private key never leaves AWS, by design — so they can only be attached to an AWS-managed endpoint (ALB, CloudFront, API Gateway), never installed directly into an nginx/Traefik config running on an EC2 instance. This is the actual reason this phase's architecture puts an ALB in front of the k3s node rather than terminating TLS on the instance itself with a self-managed certificate (e.g. Let's Encrypt) — the task specifically asked to "use ACM / appropriate AWS certificate infrastructure where appropriate," and ALB/CloudFront are the only ways to actually do that. See `docs/deployment/cost-drivers.md` for the ALB's own cost, and the cheaper (but non-ACM) alternative if that cost isn't justified for your use.

## Why HTTPS matters even though Entra SSO isn't built yet

The task is explicit that HTTPS is being prepared **for** a future Microsoft Entra ID employee SSO integration — OAuth2/OIDC authorization code flows require an HTTPS redirect URI in any real deployment (Entra ID will reject a plain-HTTP redirect URI outside of `localhost`). Nothing about Entra SSO itself is implemented this phase (no client registration, no OIDC library added to `employee-service`/`employee-portal`, no `ENTRA_*` configuration anywhere) — this doc and the ACM/ALB infrastructure above exist solely so that integration has a real HTTPS endpoint to target whenever it is actually built.
