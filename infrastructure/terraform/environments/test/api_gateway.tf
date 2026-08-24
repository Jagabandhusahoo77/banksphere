# API Gateway (HTTP API) + VPC Link -> the PRIVATE ALB -> the backend
# Kubernetes services (via Traefik's existing Ingress rules — no new
# backend routing was invented). api-dev.<domain> is intended to resolve
# here (see dns.tf), not to any ALB directly. See
# docs/deployment/api-gateway.md.
module "api_gateway" {
  source = "../../modules/api_gateway"

  project_name               = var.project_name
  environment                = var.environment
  private_subnet_ids         = module.networking.private_subnet_ids
  vpc_link_security_group_id = module.security.vpc_link_security_group_id
  private_alb_listener_arn   = module.alb_private.http_listener_arn

  domain_name     = local.api_fqdn
  certificate_arn = module.acm_api_gateway.certificate_arn

  tags = local.common_tags
}
