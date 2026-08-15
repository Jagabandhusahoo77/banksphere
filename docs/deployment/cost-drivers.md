# Cost Drivers (AWS DEV/TEST, Phase 10A — k3s rework)

This is a learning environment — every design choice in this phase leans toward the cheapest option that still genuinely satisfies the requirement, and every recurring cost is listed here so it's visible, not discovered later on a bill. Figures are rough, `us-east-1`-ish, and will vary by region — check the [AWS Pricing Calculator](https://calculator.aws) for your actual region before relying on them.

## Per environment (DEV and TEST each pay this separately — nothing is shared except ECR, see below)

| Resource | Approx. monthly cost (if left running 24/7) | Notes |
|---|---|---|
| EC2 `t3.medium`, on-demand | ~$30 | The single biggest recurring cost per environment. **Stop the instance (not terminate) outside working hours to cut this roughly proportionally to hours-off.** Unlike the original Docker-Compose design, nothing automatically "comes back up" on instance start beyond k3s itself (a systemd service, restarts automatically) — the application state lives in Argo CD/etcd/PVCs, all of which persist across a stop/start on the same instance, so Argo CD simply resumes reconciling once the node is back, no redeploy needed. See `docs/deployment/k3s.md`'s node-sizing note for the memory-budget reasoning behind still using `t3.medium` despite k3s + Argo CD's added overhead versus the original Docker Compose design. |
| EBS root volume, 40GB gp3, encrypted | ~$3.50 | Raised from the original design's 30GB (`root_volume_size_gb`) — this volume now backs the OS + k3s + Argo CD + container images + **and** every `local-path-provisioner` PVC (Postgres data, KYC documents), where the original design split "OS/images" and "Postgres/KYC volumes" across the same disk but with less overhead from k3s/Argo CD's own storage needs. |
| Application Load Balancer | ~$16-20 base + usage (LCUs) | Unchanged in kind, but now fronts only ONE target (Traefik on the k3s node) instead of three (app/ops/api) — see "Why the ALB got cheaper, not more expensive, in this rework" below. |
| 2 CloudFront distributions (customer-portal, employee-portal) | Low single digits $ at DEV/TEST traffic levels | Pay-per-use (requests + data transfer), `PriceClass_100` (US/Canada/Europe edge locations only — see `infrastructure/terraform/modules/cloudfront`). No fixed monthly minimum, unlike the ALB. |
| 2 S3 buckets (customer-portal, employee-portal static builds) | Well under $1 | A frontend build is a few MB; S3 storage is ~$0.023/GB-month. Versioning (see `docs/deployment/frontend-hosting.md`) roughly doubles stored bytes in the worst case, still negligible at this scale. |
| CloudWatch: 2 alarms + 1 dashboard + custom metrics (disk/mem) | ~$1-3 | Unchanged — still EC2-node-level metrics (disk, memory via the CloudWatch Agent; CPU/status via free built-in EC2 metrics), not Kubernetes-aware. No container/Pod-level CloudWatch metrics are collected this phase — see "What's NOT built this phase" below. |
| CloudWatch Logs | ~$0.50/GB ingested + storage | Unchanged — retention capped at 14 days (`log_retention_days`). |
| SNS (alarm topic) | Effectively $0 at this volume | Unchanged. |
| SSM Parameter Store (SecureString) | $0 | Unchanged — see `docs/deployment/secrets.md`. |
| Data transfer (ALB → EC2, EC2 → internet for ECR pulls, CloudFront → S3 origin fetches) | Small, usage-dependent | Not a fixed cost; scales with actual traffic and how often you redeploy. |

**NOT provisioned, and why each would have cost more:**
- **NAT Gateway** (~$32/month + data processing) — avoided entirely; the EC2 instance sits in a public subnet instead. See `docs/deployment/networking.md`.
- **RDS** (~$12-15/month minimum for `db.t3.micro`, before storage) — avoided; Postgres runs as a Pod on the same k3s node instead. See `docs/deployment/postgresql.md`.
- **EKS** (~$0.10/hour ≈ $73/month per cluster, control plane alone, before any worker node cost) — avoided for DEV/TEST specifically because of this cost, in favor of k3s on the existing EC2 instance; EKS remains the documented PROD-only choice. See `docs/deployment/k3s.md` and `infrastructure/terraform/environments/prod/README.md`.
- **A second/third EC2 instance, multi-node k3s, or a multi-AZ deployment** — this phase is explicitly single-node per environment.
- **A customer-managed KMS key** (~$1/month/key) — ECR and SSM both use AWS-managed keys instead, at no extra cost.

## Shared across DEV and TEST (paid once)

| Resource | Approx. monthly cost | Notes |
|---|---|---|
| 8 ECR repositories, image storage | ~$0.10/GB-month | Lifecycle policy caps each repo at 30 tagged images + expires untagged images after 3 days — see `infrastructure/terraform/modules/ecr` — so this doesn't grow unbounded as the pipeline runs. Unchanged by this rework: still 6 backend services + 2 portal images (the portal images are now a reference/fallback artifact only — see `docs/deployment/helm.md` — but still built and pushed every run). |

## Why the ALB got cheaper (in effect), not more expensive, in this rework

The ALB's own base price didn't change, but its **utilization** did: the original design routed app/ops/api traffic through one ALB with three target groups and host+path-priority listener rules (see the pre-rework `docs/deployment/networking.md`, now superseded). This rework's ALB has exactly one target group and forwards everything to Traefik — app/ops traffic moved to CloudFront (pay-per-use, no fixed base cost) instead of adding to the ALB's LCU-based usage cost. Net effect: the same fixed ALB base cost now serves less traffic through it (API calls only), while CloudFront/S3 add a smaller, usage-scaled cost for the frontends — likely a net cost improvement at DEV/TEST traffic levels, though not the primary reason for the architecture change (GitOps/Kubernetes consistency was).

If ACM/HTTPS were dropped entirely as a requirement (see `docs/deployment/dns-and-https.md`), the ALB could still be removed in favor of a self-managed certificate directly on the EC2 instance — that alternative isn't built this phase, same as before.

## What's NOT built this phase (cost-relevant)

- **Container/Pod-level cost or resource monitoring** — CloudWatch here only sees the EC2 node's own disk/memory/CPU, not individual Pod resource usage. `kubectl top pods` (via `metrics-server`, not installed this phase) or a future Prometheus/Grafana stack (explicitly out of scope per `docs/00-project-overview/scope.md`) would be the natural additions.
- **Spot instances** for the EC2 node — on-demand only; a single-node cluster losing its only node to a Spot interruption would take the whole environment down, an availability trade-off not worth the ~70% discount for a learning environment that's already cost-optimized elsewhere.

## Making DEV/TEST easy to stop and destroy

- **Stop** (pause billing on compute, keep everything else): `aws ec2 stop-instances --instance-ids <id>`. k3s (and everything on it — Argo CD, the application, its PVCs) restarts automatically on the next instance start, no manual intervention needed and no redeploy triggered — see the EC2 row above. EBS volumes (and their data — including Postgres/KYC PVC contents) are preserved while stopped; you still pay for EBS storage while stopped, just not compute.
- **Destroy** (tear down everything Terraform created for one environment): `terraform destroy` from that environment's directory. **Does not touch the other environment, the shared ECR repositories** (those live in DEV's state — see `infrastructure/terraform/environments/dev/ecr.tf` — destroying TEST never touches them; destroying DEV would, so check TEST doesn't still need those images first), **or the `gitops/` Git state** (Argo CD's registration is a cluster-side resource, gone with the cluster — the Helm chart/values files in Git are unaffected and ready to redeploy to a fresh cluster).
- Neither environment has `enable_deletion_protection` set on its ALB, and no resource in either environment has `prevent_destroy` — consistent with "make DEV and TEST easy to stop and destroy," at the cost of also making them easy to accidentally destroy. Be careful with `terraform destroy`'s target.
