variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "namespace" {
  description = "Kubernetes namespace this environment's workloads live in. Per the task's own instruction: banksphere-dev / banksphere-test / (future, not built) banksphere-prod."
  type        = string
}

variable "k3s_version" {
  description = "Pinned k3s release — never installed as \"latest\", for the same reproducibility reason image tags are pinned to a commit SHA rather than \"latest\"."
  type        = string
  default     = "v1.30.6+k3s1"
}

variable "argocd_version" {
  description = "Pinned Argo CD release tag (manifest bundle version), not the \"stable\" floating channel."
  type        = string
  default     = "v2.13.2"
}

variable "gitops_repo_url" {
  description = "Git URL Argo CD watches for this environment's desired state (see /gitops in this repository, or a future split-out GitOps repository — see gitops/README.md). REQUIRED — no default, since it's specific to how you host this repository."
  type        = string
}

variable "gitops_repo_revision" {
  description = "Branch/tag Argo CD tracks."
  type        = string
  default     = "main"
}

variable "gitops_apps_path" {
  description = "Path within gitops_repo_url that is this environment's Helm values root (contains Chart.yaml or is pointed at the shared chart with an environment values file — see gitops/README.md for the exact layout)."
  type        = string
}

variable "ecr_secret_name" {
  description = "Name of the Kubernetes docker-registry Secret the node's ECR-credential-refresh script maintains, and that the Helm chart's pod specs reference as imagePullSecrets. Must match on both sides — see gitops/apps/banksphere/values.yaml's own default of the same name."
  type        = string
  default     = "ecr-registry-credentials"
}

variable "ecr_secret_refresh_hours" {
  description = "How often the systemd timer re-fetches an ECR auth token and updates the Kubernetes Secret. ECR tokens expire after 12h — refreshing well before that avoids a window where pulls start failing."
  type        = number
  default     = 6
}

variable "ssm_parameter_path_prefix" {
  description = "This environment's SSM path (e.g. \"/banksphere/dev/\") — read once at bootstrap to populate the Kubernetes app-secrets Secret. See docs/deployment/secrets.md. Unlike the ECR credential, these values do not expire, so this is a one-time kubectl create, not a refreshed timer."
  type        = string
}

variable "app_secret_name" {
  description = "Name of the Kubernetes Secret holding DB_PASSWORD/JWT_SECRET/EMPLOYEE_JWT_SECRET — must match gitops/apps/banksphere/values.yaml's own default of the same name."
  type        = string
  default     = "banksphere-app-secrets"
}
