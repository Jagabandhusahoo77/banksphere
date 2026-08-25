# Pure Route53 RECORD management — deliberately no zone creation here
# (that's the environment root's job, e.g. environments/dev/dns.tf) to
# avoid a circular module dependency: zone creation must happen BEFORE
# the ACM certificates (which need a zone_id to write DNS validation
# records into), but these alias records must happen AFTER the ALB and
# API Gateway resources they point at exist. Splitting "own the zone"
# from "write records into it" breaks that cycle cleanly. See
# docs/deployment/dns-and-https.md.
#
# Route53 is DNS only here, per the target architecture's own
# instruction — every record below is a plain ALIAS to an AWS-managed
# endpoint (the public ALB or API Gateway's custom domain), never a
# proxy/redirect Route53 itself performs.
#
# api_fqdn now points at API GATEWAY (not the ALB) — the API Gateway +
# VPC Link + private ALB chain is the actual backend entry point now.
# app_fqdn now points at the PUBLIC ALB (not CloudFront — CloudFront was
# removed; the frontend is served from Kubernetes behind the public ALB
# instead). See modules/api_gateway and modules/alb.
#
# Every resource here is conditional on var.zone_id being non-empty.

resource "aws_route53_record" "api" {
  count = var.zone_id != "" && var.api_fqdn != "" && var.api_gateway_domain_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.api_fqdn
  type    = "A"

  alias {
    name                   = var.api_gateway_domain_name
    zone_id                = var.api_gateway_hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "app" {
  count = var.zone_id != "" && var.app_fqdn != "" && var.alb_dns_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.app_fqdn
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "ops" {
  count = var.zone_id != "" && var.ops_fqdn != "" && var.alb_dns_name != "" ? 1 : 0

  zone_id = var.zone_id
  name    = var.ops_fqdn
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}
