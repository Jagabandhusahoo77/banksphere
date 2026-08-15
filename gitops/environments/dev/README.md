# DEV environment

Reconciled by the Argo CD `Application` named `banksphere-dev` (in the
`argocd` namespace), registered once at node bootstrap by
[`infrastructure/terraform/modules/k3s`](../../../infrastructure/terraform/modules/k3s).
Deploys [`../../apps/banksphere`](../../apps/banksphere) with
`values.yaml` + `values-dev.yaml` layered on top, into the
`banksphere-dev` Kubernetes namespace on the DEV k3s node.

## What's real vs. still a placeholder

- **Helm chart**: complete, `helm lint`/`helm template`/`kubeconform`-clean
  against `values-dev.yaml` — see the Phase 10A report.
- **`values-dev.yaml`**: every `TODO`-marked field (image registry,
  ingress host, CORS origins) is genuinely unset — this environment has
  never been `terraform apply`'d (see
  [`infrastructure/README.md`](../../../infrastructure/README.md)'s own
  "AWS credentials" note), so the values that only exist AFTER a real
  apply (the ECR registry hostname, whether a domain was configured)
  cannot be filled in yet. Filling them in is part of actually standing
  DEV up, not an oversight here.
- **Corresponding Terraform**: [`infrastructure/terraform/environments/dev`](../../../infrastructure/terraform/environments/dev) —
  `fmt`-clean and `validate`-clean, never `apply`'d.

## Reaching this environment (once actually deployed)

- API: `https://api-dev.<domain>` once a domain is configured, otherwise
  the ALB's own AWS-assigned DNS name (`terraform output alb_dns_name`
  in `infrastructure/terraform/environments/dev`).
- Customer portal: `https://app-dev.<domain>` (CloudFront + S3 — not
  this Kubernetes namespace, see `docs/deployment/frontend-hosting.md`).
- Employee portal: `https://ops-dev.<domain>` (same, CloudFront + S3).
