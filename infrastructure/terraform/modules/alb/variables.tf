variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
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
