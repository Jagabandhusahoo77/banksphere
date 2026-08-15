# DEV/TEST Environment Isolation (Phase 10A — k3s rework)

## The rule

DEV and TEST share **nothing** except the ECR image registry. Everything else — network, compute, database, persistent storage, secrets, application configuration — is fully separate, per the task's own explicit instruction. Unchanged by the k3s rework; what changed is which resource TYPE implements each layer.

## What "separate" actually means, resource by resource

| Layer | How isolation is enforced |
|---|---|
| VPC | Two entirely separate VPCs (`10.20.0.0/16` DEV, `10.30.0.0/16` TEST by default) — not two subnets of one shared VPC. No network path between them at all (no peering, no Transit Gateway). |
| EC2 / k3s node | Two entirely separate instances, each running its own single-node k3s cluster, in two entirely separate `terraform apply` runs, from two entirely separate Terraform state files. Not two namespaces on one shared cluster — two genuinely separate clusters. |
| Kubernetes namespace | `banksphere-dev` / `banksphere-test` — belongs to the layer above (two separate clusters), not an isolation mechanism in its own right here, but still the namespace each environment's Argo CD `Application`/Helm release targets. |
| Postgres | Two entirely separate Pods, each on its own node's own `local-path-provisioner` PVC (backed by that node's own EBS volume), each with an independently `random_password`-generated credential. See `docs/deployment/postgresql.md`. |
| KYC documents | Two entirely separate PVCs, on two entirely separate EBS volumes. See `docs/deployment/kyc-storage.md`. |
| Secrets | Two entirely separate SSM Parameter Store paths (`/banksphere/dev/*` vs `/banksphere/test/*`), read once at node bootstrap into two entirely separate Kubernetes Secrets (one per cluster — there's no shared Secret store to accidentally cross) — and, critically, **enforced by IAM, not just by naming**: DEV's EC2 instance role's policy only grants `ssm:GetParameter` under `/banksphere/dev/*`. See `docs/deployment/secrets.md` and `infrastructure/terraform/modules/ec2`'s IAM policy. |
| `JWT_SECRET`/`EMPLOYEE_JWT_SECRET` | Independently `random_password`-generated per environment — a token issued by DEV's customer-service is not a valid token against TEST's customer-service (different signing key), matching the same "employee vs customer, separate keys" principle ADR-006 already established, applied here across environments too. |
| Application config (CORS origins, image tags, etc.) | Independently rendered per environment from that environment's own `values-<env>.yaml` in the GitOps repo — see `docs/deployment/helm.md`. A DEV GitOps commit updates only `values-dev.yaml`; TEST's Argo CD `Application` never reads that file. |

## What IS shared, and why that's compliant with the isolation requirement

**ECR repositories** — the container image registry. An image registry is infrastructure for *distributing* a build artifact, not environment-specific *configuration* — the task's isolation list is database/storage/secrets/configuration, and a registry is none of those. Sharing it is also what makes "build once, deploy the same image to DEV then TEST" (the pipeline's actual design — see `azure-pipelines.yml` and `docs/deployment/gitops.md`) possible at all for the six backend services — duplicating the registry per environment would mean building two different images from the same commit, a worse guarantee, not a better one. (The two frontend static builds are a deliberate, narrower exception to this — see `docs/deployment/frontend-hosting.md`.)

See `infrastructure/terraform/environments/dev/ecr.tf` for exactly where these repositories are created (once, in DEV's state) and `environments/test/ecr.tf` for how TEST reads them back (a read-only `data "aws_ecr_repository"` lookup, never a second `aws_ecr_repository` resource). The `gitops/` **Git repository itself** (or directory, this phase — see `gitops/README.md`) is also, necessarily, shared — it holds both `values-dev.yaml` and `values-test.yaml` side by side — but each environment's Argo CD `Application` only ever reads its own values file, so this is a shared *transport*, not shared *state*, matching the same reasoning as ECR.

## Practical consequence: apply order matters

DEV must be applied before TEST, at least once — TEST's `data "aws_ecr_repository"` lookups will fail to resolve if the repositories don't exist yet. This is enforced naturally by Terraform (a `terraform plan`/`apply` in `environments/test` will error clearly if DEV hasn't created them), not by an explicit dependency Terraform can't otherwise express, since the two environments are genuinely separate root modules/state files with no other coupling.
