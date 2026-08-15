# Two certificates, deliberately in two different regions — see
# providers.tf's comment and docs/deployment/dns-and-https.md.

# Regional cert (same region as the ALB) for api-dev.<domain>.
module "acm_alb" {
  source = "../../modules/acm"
  providers = {
    aws = aws
  }

  project_name    = var.project_name
  environment     = var.environment
  cert_purpose    = "alb"
  primary_fqdn    = local.api_fqdn
  route53_zone_id = local.effective_zone_id
  tags            = local.common_tags
}

# us-east-1 cert, covering BOTH portal hostnames as one cert with two
# SANs, for the two CloudFront distributions.
module "acm_cloudfront" {
  source = "../../modules/acm"
  providers = {
    aws = aws.us_east_1
  }

  project_name     = var.project_name
  environment      = var.environment
  cert_purpose     = "cloudfront"
  primary_fqdn     = local.app_fqdn
  additional_fqdns = local.ops_fqdn != "" ? [local.ops_fqdn] : []
  route53_zone_id  = local.effective_zone_id
  tags             = local.common_tags
}
