# GitOps — Argo CD (Phase 10A rework)

## The rule this rework exists to enforce

**Azure DevOps builds and pushes images; it never deploys the application directly.** No SSH, no `aws ssm send-command` running `kubectl apply`/`docker compose up` on a node — that was the original Phase 10A design (`deploy.sh` over SSM), explicitly superseded by the architecture correction. Instead:

```text
Developer → Azure DevOps → build + test + scan → Docker image → Amazon ECR
    → Azure DevOps updates gitops/apps/banksphere/values-<env>.yaml's image.tag
    → git commit + push to this repository
    → Argo CD (already running on the environment's k3s node) detects the change
    → Argo CD reconciles: renders the Helm chart, applies the result to the cluster
```

The pipeline's role ends at the git commit. Everything after that is Argo CD's reconcile loop — see `docs/deployment/azure-pipelines.md` for the exact stages, and `docs/deployment/k3s.md` for how Argo CD itself got onto the node in the first place (registered once, at node bootstrap, by `infrastructure/terraform/modules/k3s`).

## Why GitOps instead of the pipeline deploying directly

- **Git becomes the single source of truth for "what should be running."** `git log` on `gitops/apps/banksphere/values-dev.yaml` is a complete, auditable deploy history — every image tag ever deployed to DEV, who committed it, when — without needing pipeline run logs or SSH session history to reconstruct it.
- **Rollback is `git revert`,** not a bespoke script or a manual SSM command reconstructing a previous state.
- **The cluster is self-healing against drift.** Argo CD's `syncPolicy.automated.selfHeal: true` means a manual `kubectl edit` on the cluster (accidental or otherwise) gets reverted back to what Git says on the next reconcile — the running cluster can't silently diverge from what's committed.
- **The pipeline's AWS credentials never need cluster-level access.** The old SSM-based design needed `ssm:SendCommand` scoped to the EC2 instance, which is a fairly broad "run arbitrary shell commands on this box" capability. This rework's pipeline needs only ECR push + write access to this Git repository — a smaller, more auditable blast radius.

## The Argo CD `Application`

Registered once, at node bootstrap (see `docs/deployment/k3s.md` step 7) — never re-created or modified by the pipeline:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: banksphere-<env>
  namespace: argocd
spec:
  source:
    repoURL: <gitops_repo_url>
    targetRevision: main
    path: gitops/apps/banksphere
    helm:
      valueFiles: [values.yaml, values-<env>.yaml]
  destination:
    server: https://kubernetes.default.svc
    namespace: banksphere-<env>
  syncPolicy:
    automated: { prune: true, selfHeal: true }
    syncOptions: [CreateNamespace=true]
```

`prune: true` means a resource removed from the chart (e.g. a service deleted from `values.yaml`) is actually deleted from the cluster on the next sync, not just left orphaned. Argo CD's default poll interval is 3 minutes; a webhook could shorten that but isn't configured this phase (would need a publicly reachable Argo CD webhook endpoint, which the current design deliberately doesn't expose — see below).

## Pinned versions, not "stable"

Both k3s and Argo CD are installed at explicit, pinned release tags (`k3s_version`/`argocd_version` Terraform variables) — never a floating "latest"/"stable" channel. Matches this phase's existing immutable-image-tag philosophy (see `infrastructure/terraform/modules/ecr`): a re-run of the same bootstrap script should produce the same cluster software, not whatever happened to be current that day.

## Access — no public Argo CD UI this phase

Argo CD's own UI/API is not exposed publicly (no Ingress rule routes to `argocd-server`). Access is via `kubectl port-forward -n argocd svc/argocd-server 8080:443` over an SSM Session Manager session — same no-open-inbound-port philosophy as the rest of this phase's EC2 access model (see `docs/deployment/networking.md`'s "SSH is closed by default" section, and `environments/dev/outputs.tf`'s `argocd_namespace` output, which documents this exact command).

## Mono-repo GitOps (this phase) vs. a split-out repository (future option)

GitOps state (`gitops/`) lives in the same repository as the application code this phase — see `gitops/README.md` for the reasoning (simpler for a two-environment demo, one fewer credential surface). `gitops_repo_url`/`gitops_apps_path` (Terraform variables on `modules/k3s`) are both independently configurable specifically so a future phase could split `gitops/` into its own repository without changing the Helm chart or the Argo CD `Application` shape at all.

## What's NOT built this phase

- **Argo CD SSO / RBAC beyond the default admin** — out of scope; access is via port-forward + the default admin credential (retrieved via `kubectl -n argocd get secret argocd-initial-admin-secret`), acceptable for a DEV/TEST learning environment with no public UI exposure.
- **ApplicationSets / multi-app templating** — this phase has exactly one `Application` per environment (two total: `banksphere-dev`, `banksphere-test`), not enough repetition yet to justify the extra abstraction.
- **A PROD `Application`** — not registered anywhere; see `gitops/environments/prod/README.md`.
- **Argo CD Notifications / Slack integration** — sync status is checked via `kubectl -n argocd get applications` or the port-forwarded UI, not pushed anywhere automatically.
