# GitOps — Argo CD source of truth for DEV/TEST

This directory holds the **desired state** of BankSphere's Kubernetes
workloads, reconciled continuously by **Argo CD** running on each
environment's k3s node. See [`docs/deployment/gitops.md`](../docs/deployment/gitops.md)
and [`docs/deployment/helm.md`](../docs/deployment/helm.md) for the full
design; this README just orients you around the files.

## Structure

```text
gitops/
├── apps/banksphere/       The Helm chart — everything actually deployed
│   ├── Chart.yaml
│   ├── values.yaml          Chart-wide defaults (ports, health paths, DB names, service wiring)
│   ├── values-dev.yaml      DEV overrides (image registry, ingress host, CORS)
│   ├── values-test.yaml     TEST overrides (same shape as DEV)
│   └── templates/           Deployments/Services/PVCs/Ingress/ConfigMap for postgres, the 6 backend services, and (disabled by default) the 2 portals
└── environments/           Per-environment orientation docs (this dir does NOT contain more Helm values — see below)
    ├── dev/README.md
    ├── test/README.md
    └── prod/README.md        NOT IMPLEMENTED — placeholder only, mirrors terraform/environments/prod/README.md
```

`environments/<env>/` is deliberately NOT a second place Helm values
live — `apps/banksphere/values-<env>.yaml` is the only override
mechanism, referenced directly by that environment's Argo CD
`Application.spec.source.helm.valueFiles`. Splitting values across two
locations would just create a place for the two to silently drift.
`environments/<env>/README.md` instead answers "what actually runs
here and how do I reach it" — a human-facing pointer, not more config.

## Deployment flow (as actually built this phase)

```text
Developer → Azure DevOps → build + test + scan → Docker image → Amazon ECR
    → Azure DevOps updates apps/banksphere/values-<env>.yaml's image.tag
    → git commit to this repository
    → Argo CD (running on the env's k3s node) detects the change and reconciles
    → k3s applies the rendered Helm chart
```

CI never runs `kubectl apply`/SSHes into the node to deploy the
application — see [`azure-pipelines.yml`](../azure-pipelines.yml) and
[`docs/deployment/gitops.md`](../docs/deployment/gitops.md). The ONE
`kubectl apply` that isn't Argo CD reconciling is the Argo CD
`Application` resource itself, registered once by
[`infrastructure/terraform/modules/k3s`](../infrastructure/terraform/modules/k3s)
at node bootstrap — after that, this directory is the only thing that
changes an environment's running workloads.

## What lives where (today)

- **DEV and TEST**: both real, both Helm-chart-defined here, both
  `helm lint`/`helm template`/`kubeconform`-validated (see the Phase 10A
  report). Neither has actually been deployed to AWS this phase — see
  `environments/dev/README.md` and `environments/test/README.md` for
  exactly what's still needed before a real deploy.
- **PROD**: not implemented. `environments/prod/README.md` is a
  placeholder only, same spirit as
  [`infrastructure/terraform/environments/prod/README.md`](../infrastructure/terraform/environments/prod/README.md).
  No Argo CD `Application` targets it; no `values-prod.yaml` exists in
  the chart.

## Mono-repo vs. a split-out GitOps repository

This phase keeps GitOps state in the same repository as the application
code (`gitops/` at the repo root) rather than a separate repository —
simpler for a two-environment demo project, one fewer credential/access
surface to manage. `gitops_repo_url`/`gitops_apps_path` (see
`infrastructure/terraform/modules/k3s`'s variables) are both
configurable specifically so a future phase can split this directory
into its own repository without changing the Helm chart or Argo CD
Application shape — only those two Terraform variables would need to
point elsewhere.
