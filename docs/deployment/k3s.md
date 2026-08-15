# k3s — single-node Kubernetes for DEV/TEST (Phase 10A rework)

## What changed, and why

The original Phase 10A design ran DEV/TEST as a single EC2 instance running **Docker Compose**. Per the architecture correction, DEV/TEST now run as a single EC2 instance running **k3s** — a lightweight, single-binary Kubernetes distribution — with the application deployed via Helm + Argo CD (GitOps), never `docker compose up` again. Docker Compose remains, unchanged, as the **local development** stack only (`docker/local/`) — see that directory's own README for why it's explicitly out of scope for this change.

**Why k3s over full upstream Kubernetes (kubeadm) or a managed control plane (EKS) for DEV/TEST:**
- **Single binary, single process, minutes to install** (`curl -sfL https://get.k3s.io | sh`) — no separate etcd cluster, no control-plane/worker split to provision, appropriate for a single-node, cost-conscious environment.
- **Bundles Traefik (ingress) and `local-path-provisioner` (dynamic PV/PVC storage)** out of the box — see `docs/deployment/ingress.md` and the Postgres/KYC storage docs — so nothing extra needs installing for either.
- **EKS was explicitly ruled out for DEV/TEST** by the task's own scope boundary (EKS is PROD-only, and not built at all this phase) — EKS's control-plane cost (~$0.10/hour, ~$73/month, per cluster) and operational overhead aren't justified for a learning environment; see `docs/deployment/cost-drivers.md`.

## What actually runs on the node

One EC2 instance, one k3s server (`k3s server`, no separate agent nodes — this is a genuinely single-node cluster, not a scaled-down multi-node one), hosting:
- **k3s system components**: the API server, Traefik (ingress), CoreDNS, `local-path-provisioner` — all bundled, none separately installed.
- **Argo CD** (`argocd` namespace) — installed via the official pinned-version install manifest (never the "stable" floating channel — see `docs/deployment/gitops.md`).
- **The application** (`banksphere-dev`/`banksphere-test` namespace) — Postgres, the six backend services, and (disabled by default) the two portal reference Deployments — all reconciled by Argo CD from the Helm chart in `gitops/`, never applied directly by this bootstrap script. See `docs/deployment/helm.md`.

## Bootstrap sequence (`infrastructure/terraform/modules/k3s/templates/bootstrap.sh.tpl`)

Runs exactly once, as EC2 user-data, on first boot:

1. Install k3s (pinned version — never "latest," same reproducibility reasoning as pinned image tags).
2. Wait for the node to report `Ready`.
3. Install `helm`/`kubectl` CLIs — for operator troubleshooting only; **not** the deploy path (see step 7).
4. Create the application namespace (`banksphere-dev`/`banksphere-test`).
5. Write and run an ECR-credential-refresh script (see "ECR image pulls" below), then provision `DB_PASSWORD`/`JWT_SECRET`/`EMPLOYEE_JWT_SECRET` from SSM into a one-time Kubernetes Secret (`banksphere-app-secrets`).
6. Install Argo CD (pinned version).
7. Register **exactly one** Argo CD `Application` resource pointing at `gitops/apps/banksphere` — **the only application-level `kubectl apply` this script, or any part of this phase's automation, ever performs.** Everything downstream — every Deployment, Service, PVC, Ingress the application actually needs — comes from Argo CD reconciling the Helm chart from Git, not from this script or from a human running `kubectl apply` by hand. See `docs/deployment/gitops.md`.

## ECR image pulls — no static AWS credentials in the cluster

A Kubernetes `kubernetes.io/dockerconfigjson` Secret (name: `ecr-registry-credentials`, referenced as `imagePullSecrets` by every Pod spec in the Helm chart) holds the pull credential. ECR authorization tokens expire after 12 hours, so a systemd timer re-runs the refresh script every `ecr_secret_refresh_hours` (default 6) — well inside that window — fetching a fresh token via the node's own **IAM instance role** (through IMDSv2, no long-lived AWS access keys anywhere on the node or in any Kubernetes manifest) and updating the Secret in place (`kubectl apply` of a `--dry-run=client`-generated manifest, idempotent).

**Not implemented this phase** (documented, not silently omitted): a kubelet image-credential-provider plugin, which would let the kubelet fetch ECR tokens itself without a refreshed Secret at all — a more "native" approach, but meaningfully more complex to wire up correctly for a single-node demo cluster. The systemd-timer approach is simpler, easier to reason about, and sufficient at this scale.

## Node sizing

`t3.medium` (2 vCPU / 4GB) by default — see `infrastructure/terraform/modules/ec2`'s `instance_type` variable description for the honest memory-budget accounting (k3s + Argo CD control-plane overhead, six Spring Boot JVMs each capped by an explicit `resources.limits.memory` in the Helm chart, plus Postgres). If the node shows sustained memory pressure in practice, `t3.large` (8GB) is the documented next step — not pre-emptively provisioned, per this phase's cost-conscious-by-default posture. See `docs/deployment/cost-drivers.md`.

## What's explicitly NOT built this phase

- **Multi-node k3s** (agents joining the server) — single-node only, per the task's own scope boundary.
- **EKS** — PROD-only, not built at all this phase. See `infrastructure/terraform/environments/prod/README.md`.
- **A remote Terraform backend for k3s/cluster state** — Kubernetes' own state (etcd, embedded in k3s's SQLite-backed datastore for a single-node cluster) is separate from Terraform state and isn't a gap introduced by this rework; Terraform still only manages the AWS resources (the EC2 instance, its IAM role, etc.), never Kubernetes objects directly (aside from the one bootstrap-time `Application` registration).
