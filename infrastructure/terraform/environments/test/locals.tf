locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # The k3s node's Traefik ingress port — the ALB's only target. Passed
  # into the security module (to open it, ALB-only) and modules/alb (to
  # point at it), so the two can never silently drift apart. See
  # docs/deployment/ingress.md.
  app_ports = {
    ingress_http = 80
  }

  ssm_parameter_path_prefix = "/${var.project_name}/${var.environment}/"

  # DNS names this environment expects to serve — computed once, reused
  # by acm.tf/dns.tf/cloudfront.tf/gitops registration. Empty strings
  # (not real hostnames) whenever domain_name isn't set; every downstream
  # resource that consumes these is itself already conditional on
  # domain_configured, so an empty string here never becomes a real,
  # accidentally-created DNS record. See docs/deployment/dns-and-https.md.
  domain_configured = var.domain_name != ""
  app_fqdn          = local.domain_configured ? "app-${var.environment}.${var.domain_name}" : ""
  ops_fqdn          = local.domain_configured ? "ops-${var.environment}.${var.domain_name}" : ""
  api_fqdn          = local.domain_configured ? "api-${var.environment}.${var.domain_name}" : ""
}
