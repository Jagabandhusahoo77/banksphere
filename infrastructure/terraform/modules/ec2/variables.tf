variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  description = "Region this instance runs in — used to scope IAM policy ARNs and the KMS-via-SSM condition. Passed explicitly rather than read via a provider-region data source."
  type        = string
}

variable "subnet_id" {
  description = "Public subnet to launch the instance in (from the networking module)."
  type        = string
}

variable "security_group_ids" {
  type = list(string)
}

variable "instance_type" {
  description = "Cost-conscious default: t3.medium (2 vCPU / 4GB). k3s itself has a light footprint (~500MB-1GB for the control plane on a single node), leaving room for six Spring Boot JVMs + Postgres, each capped by an explicit resources.limits.memory in the Helm chart (see gitops/apps/banksphere/values.yaml) so the node stays within budget. Bump to t3.large (8GB) if the node shows sustained memory pressure — see docs/deployment/cost-drivers.md."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size_gb" {
  description = "Also backs k3s's local-path-provisioner PersistentVolumes (Postgres data, KYC documents) — see docs/deployment/postgresql.md and docs/deployment/kyc-storage.md."
  type        = number
  default     = 40
}

variable "ecr_repository_arns" {
  description = "ARNs of the ECR repositories this instance is allowed to pull from (least privilege — not admin access to all of ECR)."
  type        = list(string)
  default     = []
}

variable "ssm_parameter_path_prefix" {
  description = "SSM Parameter Store path prefix this instance may read (e.g. \"/banksphere/dev/\") — scopes the instance role so a DEV instance cannot read TEST's secrets and vice versa. Must end with a trailing slash."
  type        = string
}

variable "cloudwatch_agent_config" {
  description = "Full contents of the CloudWatch Agent JSON config (see modules/ec2/templates/cloudwatch-agent-config.json.tpl), read via file() by the caller."
  type        = string
}

variable "user_data_extra" {
  description = "Bootstrap script content appended after this module's own CloudWatch Agent install step — in practice, modules/k3s's rendered bootstrap_script output. Kept as a generic string input (rather than this module knowing anything about k3s) so modules/ec2 stays a plain, reusable \"provision an EC2 instance\" module, not coupled to any particular runtime choice."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
