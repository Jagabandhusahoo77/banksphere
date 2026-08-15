# TEST environment

Reconciled by the Argo CD `Application` named `banksphere-test` (in the
`argocd` namespace), registered once at node bootstrap by
[`infrastructure/terraform/modules/k3s`](../../../infrastructure/terraform/modules/k3s).
Deploys [`../../apps/banksphere`](../../apps/banksphere) with
`values.yaml` + `values-test.yaml` layered on top, into the
`banksphere-test` Kubernetes namespace on the TEST k3s node — its own
node, its own namespace, its own Postgres/KYC volumes, entirely separate
from DEV except for the shared ECR image registry (an image registry is
not environment configuration — see
[`infrastructure/terraform/environments/dev/ecr.tf`](../../../infrastructure/terraform/environments/dev/ecr.tf)'s
own comment on why it's owned by DEV's Terraform state regardless).

## What's real vs. still a placeholder

Identical status to [`../dev/README.md`](../dev/README.md) — same
`TODO`-marked, genuinely-unset fields in `values-test.yaml` for the same
reason (no `terraform apply` has been run against
[`infrastructure/terraform/environments/test`](../../../infrastructure/terraform/environments/test)
this phase).

## Reaching this environment (once actually deployed)

- API: `https://api-test.<domain>` once a domain is configured,
  otherwise the ALB's own AWS-assigned DNS name.
- Customer portal: `https://app-test.<domain>` (CloudFront + S3).
- Employee portal: `https://ops-test.<domain>` (CloudFront + S3).

## Promotion

Per `azure-pipelines.yml`, the same image that passed DEV smoke tests is
promoted to TEST (the image is never rebuilt) — only the GitOps commit
that updates `values-test.yaml`'s `image.tag` is new. See
`docs/deployment/gitops.md` and `docs/deployment/azure-pipelines.md`.
