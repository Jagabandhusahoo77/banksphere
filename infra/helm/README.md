# Helm

Helm chart(s) for packaging and deploying BankSphere to Kubernetes.

## Structure

```text
helm/
└── banksphere/
    ├── charts/               Subchart dependencies
    ├── templates/            Kubernetes resource templates
    ├── values.yaml           Default values
    ├── values-dev.yaml       Dev environment overrides
    ├── values-staging.yaml   Staging environment overrides
    ├── values-prod.yaml      Prod environment overrides
    └── Chart.yaml            Chart metadata
```

No chart has been implemented yet. This chart will eventually be the deployable unit referenced by Argo CD application definitions in [`gitops/`](../../gitops/README.md).
