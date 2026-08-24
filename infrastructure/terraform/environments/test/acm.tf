# Two regional certificates, both in the SAME region as everything else
# now (aws_region) — CloudFront's us-east-1 requirement no longer
# applies to anything in this environment, since CloudFront was removed.
# See providers.tf: the us_east_1 provider alias was removed along with
# it, since nothing references it anymore.

# For the PUBLIC ALB (app-dev.<domain> — the frontend entry point, see
# modules/alb's public instantiation in alb.tf).
module "acm_public_alb" {
  source = "../../modules/acm"
  providers = {
    aws = aws
  }

  project_name    = var.project_name
  environment     = var.environment
  cert_purpose    = "public-alb"
  primary_fqdn    = local.app_fqdn
  route53_zone_id = local.effective_zone_id
  tags            = local.common_tags
}

# For API Gateway's custom domain (api-dev.<domain>) — reused by
# modules/api_gateway, NOT by the private ALB itself (the private ALB
# has no certificate/HTTPS listener of its own; it's only ever reached
# from inside the VPC via the VPC Link, so TLS terminates at API Gateway
# instead — see docs/deployment/api-gateway.md).
module "acm_api_gateway" {
  source = "../../modules/acm"
  providers = {
    aws = aws
  }

  project_name    = var.project_name
  environment     = var.environment
  cert_purpose    = "api-gateway"
  primary_fqdn    = local.api_fqdn
  route53_zone_id = local.effective_zone_id
  tags            = local.common_tags
}
