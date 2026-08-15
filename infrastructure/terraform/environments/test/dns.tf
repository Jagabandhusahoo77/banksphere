# Zone creation lives here (not in modules/dns) — see that module's own
# comment for why: it must be resolved before the ACM certificates
# (acm.tf), while the actual alias records must be written after the ALB
# and CloudFront distributions exist. This file's own module.dns call
# happens last for that reason.

resource "aws_route53_zone" "this" {
  count = local.domain_configured && var.route53_zone_id == "" && var.create_hosted_zone ? 1 : 0

  name = var.domain_name

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.domain_name}"
  })
}

locals {
  effective_zone_id = var.route53_zone_id != "" ? var.route53_zone_id : (length(aws_route53_zone.this) > 0 ? aws_route53_zone.this[0].zone_id : "")
}

module "dns" {
  source = "../../modules/dns"

  zone_id = local.effective_zone_id

  api_fqdn     = local.api_fqdn
  alb_dns_name = module.alb.dns_name
  alb_zone_id  = module.alb.zone_id

  app_fqdn                   = local.app_fqdn
  app_cloudfront_domain_name = module.cloudfront.distribution_domain_names["customer-portal"]

  ops_fqdn                   = local.ops_fqdn
  ops_cloudfront_domain_name = module.cloudfront.distribution_domain_names["employee-portal"]
}
