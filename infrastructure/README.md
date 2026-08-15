# Infrastructure — AWS DEV/TEST (Phase 10A — k3s + GitOps rework)

Terraform + Kubernetes (k3s) + Helm + Argo CD (GitOps) + Azure Pipelines
for running BankSphere's six backend services on AWS, and its two
frontends on S3 + CloudFront, in two isolated environments (DEV and
TEST), each on a single cost-conscious EC2 instance running a
single-node k3s cluster. **PROD is not implemented** — see
[`terraform/environments/prod/README.md`](terraform/environments/prod/README.md).

**This supersedes this phase's own first draft**, which ran DEV/TEST as
Docker Compose directly on the EC2 instance. That approach is now
**local-development-only** — see [`docker/README.md`](../docker/README.md)
and [`docker/local/`](../docker/local) — never the cloud runtime. The
`infrastructure/docker/{dev,test}/` and `infrastructure/scripts/{dev,test}/`
directories from that first draft are kept for reference only; see their
own note below.

## Structure

```text
infrastructure/
├── terraform/
│   ├── modules/          networking, security, ec2, ecr, dns, monitoring, k3s, acm, s3, cloudfront, alb
│   └── environments/
│       ├── dev/           DEV root module — apply this first (also owns the shared ECR repos)
│       ├── test/          TEST root module — reads DEV's ECR repos via a data source, never recreates them
│       └── prod/          NOT IMPLEMENTED — see its own README
├── docker/                REFERENCE / LOCAL-FALLBACK ONLY — NOT the cloud runtime, see below
│   ├── dev/                the original Docker-Compose-on-EC2 design's compose file + nginx gateway config
│   └── test/               same shape
└── scripts/                REFERENCE / LOCAL-FALLBACK ONLY — NOT the cloud runtime, see below
    ├── dev/                deploy.sh (SSM-exec deploy) + backup-postgres.sh, both Docker-Compose-specific
    └── test/               same
```

`gitops/` (repository root, not under `infrastructure/`) holds the
actual DEV/TEST deploy state now — see [`gitops/README.md`](../gitops/README.md)
and [`docs/deployment/gitops.md`](../docs/deployment/gitops.md).

## `infrastructure/docker/` and `infrastructure/scripts/` — reference only, not the cloud runtime

These two directories are what the pre-rework design used to deploy
DEV/TEST directly. They are **not deleted**, per the architecture
correction's own instruction to keep them "for reference/local fallback
only" — but as of this rework:
- **`infrastructure/docker/{dev,test}/docker-compose.yml` is never run
  against a real DEV/TEST EC2 instance.** The k3s node bootstrap
  (`terraform/modules/k3s`) never installs Docker Compose or references
  these files at all.
- **`infrastructure/scripts/{dev,test}/deploy.sh`** (the SSM-exec
  deploy script) **is obsolete for cloud deploys** — superseded by the
  GitOps commit flow (`docs/deployment/gitops.md`). It is not called by
  any Terraform module or by `azure-pipelines.yml` anymore.
- **`infrastructure/scripts/{dev,test}/backup-postgres.sh`** targets a
  Docker container (`docker exec banksphere-postgres ...`) that no
  longer exists in DEV/TEST — Postgres is now a Kubernetes Pod. A
  `kubectl exec`-based rewrite is the natural replacement but **has not
  been built this phase** — see `docs/deployment/postgresql.md`'s
  "Backups" section for this stated, open gap.

If you need the actual local development stack, use
[`docker/local/docker-compose.yml`](../docker/README.md) instead — a
different, still-fully-current directory, unaffected by any of this.

## Read this first: what actually exists vs. what's prepared

- **Terraform code**: complete, `fmt`-clean, `validate`-clean (see the Phase 10A report for the actual command output).
- **Helm chart** (`gitops/apps/banksphere`): complete, `helm lint`-clean, `helm template`-clean against both `values-dev.yaml`/`values-test.yaml`, and every rendered manifest set validates against `kubeconform`. See [`docs/deployment/helm.md`](../docs/deployment/helm.md).
- **`terraform plan`**: could not be run against real AWS this phase — the AWS credentials present in this environment do not authenticate (`aws sts get-caller-identity` returns `InvalidClientTokenId`). See "Before you run `terraform apply`" below.
- **Domain**: none is configured anywhere in this repository. Every DNS/ACM resource is conditional on `domain_name` being set (see [`modules/dns`](terraform/modules/dns), [`modules/acm`](terraform/modules/acm)) — leaving it empty is a fully supported, non-erroring state; the environment still deploys and is reachable over plain HTTP on the ALB's own AWS-generated DNS name and each CloudFront distribution's own `*.cloudfront.net` name.
- **`terraform apply`**: not run. Not requested for this phase.
- **`gitops/apps/banksphere/values-{dev,test}.yaml`'s `TODO`-marked fields** (image registry, ingress host, CORS origins) are genuinely unset — they only have real values after a `terraform apply` and/or a domain being registered. See those files' own comments.

## Before you run `terraform apply`

1. **Working AWS credentials.** Run `aws sts get-caller-identity` — if it fails, fix your credentials/profile/region first. This blocked even `terraform plan` in the environment this phase was built in.
2. **A real AWS region.** Set `aws_region` in `terraform.tfvars` (copy from `terraform.tfvars.example` in each environment directory) — there is no default, by design.
3. **A GitOps repo URL.** Set `gitops_repo_url` in `terraform.tfvars` — REQUIRED, no default (see `environments/dev/variables.tf`). Points Argo CD at this same repository (or a future split-out one — see `gitops/README.md`).
4. **A remote Terraform state backend.** Both environments currently use local state — see each environment's `versions.tf`. That state file contains the generated `DB_PASSWORD`/`JWT_SECRET`/`EMPLOYEE_JWT_SECRET` values (see [`docs/deployment/secrets.md`](../docs/deployment/secrets.md)). Move to an S3 backend (versioned, SSE-KMS encrypted, public access blocked) + DynamoDB locking before any real or shared use.
5. **A domain, once you have one.** See [`docs/deployment/dns-and-https.md`](../docs/deployment/dns-and-https.md) — nothing here invents one for you.
6. **The Azure DevOps side of the pipeline.** [`azure-pipelines.yml`](../azure-pipelines.yml) assumes an AWS service connection named `banksphere-aws` (ECR push + S3 + CloudFront invalidation — **no longer** `ssm:SendCommand`/`ec2:DescribeInstances`, since the pipeline never touches an EC2 instance directly anymore) and write access back to this repository for its GitOps commits. See [`docs/deployment/azure-pipelines.md`](../docs/deployment/azure-pipelines.md).
7. Apply **DEV first**, always — it owns the shared ECR repositories (see `terraform/environments/dev/ecr.tf`); TEST's Terraform reads them via a data source and will fail to resolve if DEV hasn't been applied yet.

## Order of operations, once the above is ready

```bash
cd infrastructure/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars   # fill in aws_region and gitops_repo_url at minimum
terraform init
terraform plan     # review carefully — creates a VPC, ALB, EC2 instance running k3s+Argo CD, IAM role, 8 ECR repos, 2 S3 buckets, 2 CloudFront distributions, SSM parameters, CloudWatch alarms
terraform apply

cd ../test
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan      # creates its OWN VPC/ALB/EC2/k3s cluster/IAM/S3/CloudFront/SSM/CloudWatch — reads DEV's ECR repos, does not recreate them
terraform apply
```

Each environment's EC2 instance bootstraps itself: installs k3s, installs Argo CD, provisions the ECR pull credential and app secrets, and registers **one** Argo CD `Application` pointed at `gitops/apps/banksphere` (see `terraform/modules/k3s/templates/bootstrap.sh.tpl`). That `Application` will not find a real `image.tag` set yet (`values-<env>.yaml` defaults to `"unset"`) — the pipeline's first successful GitOps commit is what actually brings the application Pods up. See [`docs/deployment/k3s.md`](../docs/deployment/k3s.md) and [`docs/deployment/gitops.md`](../docs/deployment/gitops.md).

## Why an environment's DEV and TEST are genuinely isolated

Per this phase's own requirement, DEV and TEST share nothing except the ECR image registry (a registry is not "environment configuration"). Full detail: [`docs/deployment/environments.md`](../docs/deployment/environments.md).

## Networking, in one paragraph

Internet → ALB (HTTPS, ACM cert once a domain is configured) → the EC2 instance's k3s node → Traefik (k3s's bundled ingress controller) → the matching Kubernetes Service, by the six backend services' existing `/api/v1/...` path prefixes (see [`docs/deployment/ingress.md`](../docs/deployment/ingress.md)). Separately: `app-<env>.<domain>`/`ops-<env>.<domain>` → CloudFront → S3 (the two frontends' static builds — see [`docs/deployment/frontend-hosting.md`](../docs/deployment/frontend-hosting.md)), a path that never touches the ALB or EC2 instance at all. Postgres and the six backend services publish no host port at all. No NAT Gateway. Full detail: [`docs/deployment/networking.md`](../docs/deployment/networking.md).

## Further reading

- [`docs/deployment/k3s.md`](../docs/deployment/k3s.md) — the single-node cluster, bootstrap sequence, ECR pull credential refresh
- [`docs/deployment/gitops.md`](../docs/deployment/gitops.md) — Argo CD, the GitOps commit flow, why not deploy directly
- [`docs/deployment/helm.md`](../docs/deployment/helm.md) — the chart's structure and design choices
- [`docs/deployment/ingress.md`](../docs/deployment/ingress.md) — Traefik, the backend routing table
- [`docs/deployment/frontend-hosting.md`](../docs/deployment/frontend-hosting.md) — S3 + CloudFront for the two portals
- [`docs/deployment/dns-and-https.md`](../docs/deployment/dns-and-https.md) — domain/ACM (now two certificates)/the required manual registrar step
- [`docs/deployment/secrets.md`](../docs/deployment/secrets.md) — why SSM Parameter Store over Secrets Manager
- [`docs/deployment/postgresql.md`](../docs/deployment/postgresql.md) — why in-cluster Postgres, not RDS, this phase; backups (open gap); the future RDS recommendation
- [`docs/deployment/kyc-storage.md`](../docs/deployment/kyc-storage.md) — the PersistentVolumeClaim, and the future S3 migration
- [`docs/deployment/cost-drivers.md`](../docs/deployment/cost-drivers.md) — what actually costs money here and how to shut it off
- [`docs/deployment/environments.md`](../docs/deployment/environments.md) — the DEV/TEST isolation model in full
- [`docs/deployment/azure-pipelines.md`](../docs/deployment/azure-pipelines.md) — the pipeline's own prerequisites and design choices
- [`docs/architecture/decisions/`](../docs/architecture/decisions/) — ADR-001 through ADR-009, for the application architecture this infrastructure runs
