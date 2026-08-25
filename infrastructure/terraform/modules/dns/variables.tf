variable "zone_id" {
  description = "Route53 zone to write records into (already resolved by the caller — see environments/dev/dns.tf — either supplied or created there). Empty string skips every record in this module."
  type        = string
  default     = ""
}

variable "api_fqdn" {
  type    = string
  default = ""
}

variable "api_gateway_domain_name" {
  description = "API Gateway custom domain's own target hostname (module.api_gateway's domain_target_domain_name output) — the api_fqdn ALIAS record's target. Empty string if no domain is configured (API Gateway then has no custom domain to alias to yet)."
  type        = string
  default     = ""
}

variable "api_gateway_hosted_zone_id" {
  description = "Companion to api_gateway_domain_name (module.api_gateway's domain_hosted_zone_id output)."
  type        = string
  default     = ""
}

variable "app_fqdn" {
  type    = string
  default = ""
}

variable "alb_dns_name" {
  description = "The PUBLIC ALB's own DNS name — app_fqdn's ALIAS target (the frontend entry point)."
  type        = string
  default     = ""
}

variable "alb_zone_id" {
  type    = string
  default = ""
}

variable "ops_fqdn" {
  description = "Employee-portal hostname, e.g. \"ops-dev.example.com\" — aliases to the SAME public ALB as app_fqdn (alb_dns_name/alb_zone_id below), differentiated by Host header at the Traefik Ingress layer, not a separate AWS resource."
  type        = string
  default     = ""
}
