# Ingress — Traefik + the backend routing table (Phase 10A — k3s rework)

## Replaces: the nginx gateway container

The original design routed `api-<env>.<domain>` traffic through a hand-authored nginx container (`infrastructure/docker/<env>/gateway.nginx.conf`) running on the same EC2 instance as the backend services. This rework replaces that with a Kubernetes `Ingress` resource, reconciled by Traefik (k3s's bundled ingress controller — see `docs/deployment/k3s.md` for why no separate ingress controller was installed).

**The routing table itself did not change** — `gitops/apps/banksphere/templates/ingress.yaml` encodes the exact same path-prefix → service mapping the nginx config did, generated from each service's `ingressPaths` list in `values.yaml`:

| Path prefix | Backend service | Port |
|---|---|---|
| `/actuator/health` | customer-service | 8081 |
| `/api/v1/auth`, `/api/v1/customers` | customer-service | 8081 |
| `/api/v1/accounts` | account-service | 8082 |
| `/api/v1/transactions` | transaction-service | 8083 |
| `/api/v1/beneficiaries` | beneficiary-service | 8084 |
| `/api/v1/employees`, `/api/v1/operations`, `/api/v1/employee` | employee-service | 8085 |
| `/api/v1/kyc` | kyc-service | 8086 |

No backend `@RequestMapping` was renamed or restructured to make this possible — same property the original nginx design had.

## Traffic path

```text
ALB (api-<env>.<domain>, HTTPS if a domain is configured)
  → HTTP, to the k3s node's Traefik (the ALB's single target group — see infrastructure/terraform/modules/alb)
    → Traefik reads the Ingress resource, routes by path prefix
      → the matching Kubernetes Service (ClusterIP) → the matching Pod(s)
```

## Why `/actuator/health` is routed to a specific backend service

`infrastructure/terraform/modules/alb`'s target group needs a real, always-up, unauthenticated endpoint to poll. Traefik itself doesn't expose a ping/health endpoint on the same entrypoint used for regular ingress traffic without additional configuration this phase doesn't add (see the module's own comment for why that path was rejected as unnecessary complexity). Instead, the Ingress routes `/actuator/health` (`pathType: Exact`) to `customer-service` — a real Spring Boot Actuator endpoint, already `permitAll()` in that service's `SecurityConfig` (confirmed by inspection, not assumed), so the ALB gets a genuine "is this backend actually working" signal rather than a synthetic always-200 response. Any one of the six services would have worked equally well as the health target; this one was picked arbitrarily.

## Why a single `Ingress` resource, not one per service

All six backend services share one hostname (`api-<env>.<domain>`) and one `IngressClassName` (`traefik`) — a single `Ingress` resource with multiple path rules is the natural fit, and matches the original nginx config's own structure (one server block, multiple `location` blocks) rather than introducing per-service Ingress objects that would need to agree on the same host anyway.

## What's NOT built this phase

- **TLS at the Ingress/Traefik layer** — the ALB terminates the ACM certificate and forwards plain HTTP to Traefik; Traefik itself never handles a certificate. This is consistent with `docs/deployment/dns-and-https.md`'s reasoning (ACM certificates can only attach to AWS-managed endpoints).
- **Rate limiting / WAF** — not configured on the Ingress or the ALB this phase; out of scope per the task's own boundary.
- **A dedicated health-check path independent of a real backend service** (e.g. via Traefik's `--ping` flag routed onto the same entrypoint) — considered and rejected in favor of the simpler `/actuator/health` real-service approach above; would be a reasonable future improvement if a specific need for it arises (e.g. wanting the health check to be independent of customer-service specifically).
