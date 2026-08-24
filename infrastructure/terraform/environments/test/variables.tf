# No default here, deliberately — the task's own instruction is explicit:
# "AWS region must be configurable. Do not hard-code it throughout
# Terraform." Supply it via terraform.tfvars, -var, or TF_VAR_aws_region.
variable "aws_region" {
  description = "AWS region to deploy TEST into. REQUIRED — not defaulted, see this variable's own description. Can be the same region as DEV or a different one; nothing in this module assumes they match."
  type        = string
}

variable "project_name" {
  type    = string
  default = "banksphere"
}

variable "environment" {
  type    = string
  default = "test"
}

variable "vpc_cidr" {
  description = "TEST's own VPC CIDR — deliberately a different /16 from DEV's default (10.20.0.0/16) so the two could be peered later without a CIDR collision, even though nothing requires peering today. TEST has its own VPC regardless — see docs/deployment/environments.md."
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.30.1.0/24", "10.30.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "For the private ALB + API Gateway VPC Link — see modules/networking's own comment on why no NAT Gateway is needed for these."
  type        = list(string)
  default     = ["10.30.11.0/24", "10.30.12.0/24"]
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
  type    = bool
  default = false
}

variable "admin_cidr_blocks" {
  type    = list(string)
  default = []
}

# --- DNS/HTTPS — all optional; leave domain_name empty until you have registered one. See docs/deployment/dns-and-https.md. ---

variable "domain_name" {
  description = "Root domain to serve TEST from (TEST's hostnames become app-test.<domain>/ops-test.<domain>/api-test.<domain>) — the SAME domain_name value as DEV if you're using one domain for both (e.g. \"example-bank.com\"), just with the -test subdomain prefix instead of -dev. NOT set by default."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "If DEV already created a hosted zone for this domain (create_hosted_zone = true there), pass that SAME zone id here — do not create a second hosted zone for the same domain. If DEV manages an existing zone via route53_zone_id, use that same id here too."
  type        = string
  default     = ""
}

variable "create_hosted_zone" {
  description = "Should almost always be false for TEST if DEV already created the zone for this domain — see route53_zone_id's description. Only true if TEST is the first environment being stood up for a brand-new domain."
  type        = bool
  default     = false
}

variable "alert_email" {
  type    = string
  default = ""
}

# --- GitOps / k3s — see docs/deployment/gitops.md and docs/deployment/k3s.md ---

variable "gitops_repo_url" {
  description = "Git URL Argo CD watches for TEST's desired state — typically the SAME repository URL as DEV's, since both read from the same gitops/ tree, just a different values-test.yaml. REQUIRED — no default."
  type        = string
}

variable "gitops_repo_revision" {
  type    = string
  default = "main"
}

variable "gitops_apps_path" {
  type    = string
  default = "gitops/apps/banksphere"
}

variable "k3s_version" {
  type    = string
  default = "v1.30.6+k3s1"
}

variable "argocd_version" {
  type    = string
  default = "v2.13.2"
}
