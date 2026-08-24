# No default here, deliberately — the task's own instruction is explicit:
# "AWS region must be configurable. Do not hard-code it throughout
# Terraform." Supply it via terraform.tfvars, -var, or TF_VAR_aws_region.
variable "aws_region" {
  description = "AWS region to deploy DEV into. REQUIRED — not defaulted, see this variable's own description."
  type        = string
}

variable "project_name" {
  type    = string
  default = "banksphere"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "vpc_cidr" {
  description = "DEV's own VPC CIDR — must not overlap TEST's if the two are ever peered/connected. TEST defaults to a different /16 (see environments/test/variables.tf) specifically to avoid this trap even though nothing requires it today."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.1.0/24", "10.20.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "For the private ALB + API Gateway VPC Link — see modules/networking's own comment on why no NAT Gateway is needed for these."
  type        = list(string)
  default     = ["10.20.11.0/24", "10.20.12.0/24"]
}

variable "instance_type" {
  type    = string
  default = "t3.medium"
}

variable "root_volume_size_gb" {
  type    = number
  default = 30
}

variable "enable_ssh" {
  description = "Open port 22 on the EC2 security group. Default false — use AWS Systems Manager Session Manager instead (no open port required, access controlled entirely by IAM — see modules/ec2's instance role). Only set true if you have a specific reason SSM won't work for you, and you must also set admin_cidr_blocks."
  type        = bool
  default     = false
}

variable "admin_cidr_blocks" {
  description = "CIDR blocks allowed to SSH in, only used if enable_ssh = true. Never set this to 0.0.0.0/0 (enforced by modules/security's own variable validation)."
  type        = list(string)
  default     = []
}

# --- DNS/HTTPS — all optional; leave domain_name empty until you have registered one. See docs/deployment/dns-and-https.md. ---

variable "domain_name" {
  description = "Root domain to serve DEV from, e.g. \"example-bank.com\" (DEV's actual hostnames become app-dev.<domain>/ops-dev.<domain>/api-dev.<domain>). NOT set by default — nothing in this repository invents a domain for you. Leave \"\" and this environment deploys over plain HTTP on the ALB's DNS name until you provide one."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Existing Route53 hosted zone ID for domain_name, if you already manage it in Route53. Leave empty + set create_hosted_zone = true to have Terraform create one (you'll then need to point your registrar at the name servers it outputs)."
  type        = string
  default     = ""
}

variable "create_hosted_zone" {
  type    = bool
  default = false
}

variable "alert_email" {
  description = "Email to subscribe to the CloudWatch alarms SNS topic. Leave empty to skip (you can subscribe manually later)."
  type        = string
  default     = ""
}

# --- GitOps / k3s — see docs/deployment/gitops.md and docs/deployment/k3s.md ---

variable "gitops_repo_url" {
  description = "Git URL Argo CD watches for this environment's desired state. REQUIRED — no sensible default (specific to how/where you host this repository, or a split-out GitOps repo — see gitops/README.md)."
  type        = string
}

variable "gitops_repo_revision" {
  type    = string
  default = "main"
}

variable "gitops_apps_path" {
  description = "Path within gitops_repo_url containing the Helm chart this environment deploys — see gitops/apps/banksphere."
  type        = string
  default     = "gitops/apps/banksphere"
}

variable "k3s_version" {
  type    = string
  default = "v1.30.6+k3s1"
}

variable "argocd_version" {
  type    = string
  default = "v2.13.2"
}
