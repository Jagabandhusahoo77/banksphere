# PROD — NOT IMPLEMENTED

This directory is a deliberate placeholder. Per Phase 10A's own scope
boundary, PROD is **not implemented or provisioned in this phase** — no
`.tf` files exist here, and none should be added without a dedicated
phase for it (see [docs/00-project-overview/scope.md](../../../../docs/00-project-overview/scope.md) and this
phase's own report).

## What PROD is expected to look like (future — not built)

Per the target architecture BankSphere is working toward:

```text
PROD
  AWS EKS
```

This is a materially different shape from DEV/TEST's "single EC2
instance running a single-node k3s Kubernetes cluster" (see
[`docs/deployment/k3s.md`](../../../../docs/deployment/k3s.md)) — not a
bigger version of the same thing:

- **Compute**: EKS (a managed, multi-node Kubernetes control plane),
  not a single-node k3s cluster on one EC2 instance — real horizontal
  scaling across multiple nodes, managed control-plane upgrades, and
  higher availability than a single node can ever provide (a single-node
  cluster's node going down takes the whole environment with it,
  acceptable for DEV/TEST, not for PROD).
- **Database**: almost certainly RDS (Multi-AZ) rather than
  Postgres-as-a-Pod-on-the-node — see
  [docs/deployment/postgresql.md](../../../../docs/deployment/postgresql.md)'s
  "Future production recommendation" section for why DEV/TEST
  deliberately do NOT use RDS this phase (cost) while PROD should.
- **KYC document storage**: S3 (with versioning + encryption), not an
  EBS-backed PersistentVolumeClaim (`local-path-provisioner`) — see
  [docs/deployment/kyc-storage.md](../../../../docs/deployment/kyc-storage.md)'s
  "Future PROD architecture" section.
- **Secrets**: likely AWS Secrets Manager rather than SSM Parameter
  Store, once automatic rotation becomes a real requirement — see
  [docs/deployment/secrets.md](../../../../docs/deployment/secrets.md).
- **Networking**: private subnets for compute/database, a NAT Gateway
  (finally justified at production scale/availability requirements),
  and likely multiple Availability Zones for real redundancy.
- **Approval gate**: `azure-pipelines.yml`'s final stage is named
  "Future production approval" and deploys nothing — see the pipeline
  file at the repository root and
  [docs/deployment/azure-pipelines.md](../../../../docs/deployment/azure-pipelines.md).
- **Entra ID SSO**: HTTPS is prepared for in DEV/TEST specifically so
  this integration has somewhere to land later — it is not implemented
  in any environment yet, including PROD (not built at all this phase).

None of the above is provisioned, planned, or `terraform plan`-able
today. Building it out is future work for a dedicated PROD phase, reusing
the same `modules/` this phase built wherever they still fit (networking,
security, ecr, dns, acm, s3, cloudfront, monitoring — CloudFront/S3 for
the frontends is likely unchanged even in PROD) and very likely needing
new modules (`eks`, `rds`) that don't exist yet. `modules/k3s` and
`modules/alb` specifically are DEV/TEST-shaped (single-node bootstrap,
single-target-group) and would need real rework, not reuse, for an
EKS-fronting PROD load balancer/ingress design.
