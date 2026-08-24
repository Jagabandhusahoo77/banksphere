variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets for this ALB's own load-balancer nodes — public subnets for the internet-facing instantiation, private subnets for the internal one. At least 2, in different AZs."
  type        = list(string)
}

variable "internal" {
  description = "false = internet-facing (the public ALB). true = internal-only, no public IP, no route from the internet (the private ALB, reached only via the API Gateway VPC Link). See modules/security's alb vs. alb_private security groups, which enforce the actual traffic restriction regardless of this setting."
  type        = bool
  default     = false
}

variable "name_suffix" {
  description = "Appended to this ALB's and its target group's name, to disambiguate the two instantiations (e.g. \"-private\") without renaming/replacing the original public ALB, which has no suffix (empty string, the default) for backward compatibility with what's already been applied."
  type        = string
  default     = ""
}

variable "security_group_id" {
  type = string
}

variable "target_instance_id" {
  description = "The k3s node — its Traefik ingress controller is the ALB's only target. See docs/deployment/ingress.md for why a single ALB->single-node-Traefik hop is the right shape for a single-node k3s cluster."
  type        = string
}

variable "target_port" {
  description = "Port Traefik listens on (HTTP — the ALB terminates TLS, Traefik does not need to)."
  type        = number
  default     = 80
}

variable "certificate_arn" {
  description = "Regional ACM certificate ARN (same region as the ALB) — empty string if no domain is configured yet, in which case the ALB serves plain HTTP instead of failing. See modules/acm and docs/deployment/dns-and-https.md."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
