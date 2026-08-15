# PROD — NOT IMPLEMENTED

Deliberate placeholder, same spirit as
[`infrastructure/terraform/environments/prod/README.md`](../../../infrastructure/terraform/environments/prod/README.md).
No Argo CD `Application` targets PROD. No `values-prod.yaml` exists in
[`../../apps/banksphere`](../../apps/banksphere) — adding one without a
dedicated PROD phase would imply a deployment path that doesn't exist.

## Why PROD isn't "just another values file" on top of the same chart

Per `infrastructure/terraform/environments/prod/README.md`, PROD is a
materially different shape from DEV/TEST (EKS instead of a single k3s
node, RDS instead of in-cluster Postgres, S3 instead of a
PersistentVolumeClaim for KYC documents, private subnets + NAT Gateway,
likely Secrets Manager instead of SSM Parameter Store). Some of
`apps/banksphere`'s templates (the Postgres Deployment/PVC, the KYC PVC)
would need to become conditional or simply not apply to PROD at all —
that's chart-design work for a dedicated PROD phase, not a values file
away from what exists today.

## What would need to exist before this stops being a placeholder

- An `eks` Terraform module (and likely `rds`) — neither exists yet.
- A decision on whether PROD reuses this same chart with a `postgres.enabled: false` / external-RDS-host override, or gets its own chart — not decided, since deciding it properly requires the EKS/RDS design work itself.
- `azure-pipelines.yml`'s "Future production approval" stage actually deploying something, gated on a real approval — today it deploys nothing on purpose.
