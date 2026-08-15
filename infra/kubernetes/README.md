# Kubernetes

Raw Kubernetes manifests for BankSphere, organized by resource type.

## Structure

```text
kubernetes/
├── namespaces/       Namespace definitions (e.g. per environment/team)
├── configmaps/       Non-secret configuration
├── secrets/          Secret manifest templates (no real values committed)
├── network-policies/ Pod-to-pod traffic rules
├── ingress/          Ingress resources
└── rbac/             Roles and role bindings
```

**No secret values are committed here.** Real secrets are managed via AWS Secrets Manager / Kubernetes secrets injected at deploy time, never checked into git.

No manifests have been implemented yet. In production, cluster state will be applied via GitOps (Argo CD, see [`gitops/`](../../gitops/README.md)) rather than manually.
