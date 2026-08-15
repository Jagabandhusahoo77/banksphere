# Pure Route53 RECORD management — deliberately no zone creation here
# (that's the environment root's job, e.g. environments/dev/dns.tf) to
# avoid a circular module dependency: zone creation must happen BEFORE
# the ACM certificates (which need a zone_id to write DNS validation
# records into), but these alias records must happen AFTER the ALB and
# CloudFront distributions exist (which themselves depend on those same
# certificates). Splitting "own the zone" from "write records into it"
# breaks that cycle cleanly. See docs/deployment/dns-and-https.md.
#
# Every resource here is conditional on var.zone_id being non-empty.

resource "aws_route53_record" "api" {
  count = var.zone_id != "" && var.api_fqdn != "" && var.alb_dns_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.api_fqdn
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "app" {
  count = var.zone_id != "" && var.app_fqdn != "" && var.app_cloudfront_domain_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.app_fqdn
  type    = "A"

  alias {
    name                   = var.app_cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "ops" {
  count = var.zone_id != "" && var.ops_fqdn != "" && var.ops_cloudfront_domain_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.ops_fqdn
  type    = "A"

  alias {
    name                   = var.ops_cloudfront_domain_name
    zone_id                = var.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
