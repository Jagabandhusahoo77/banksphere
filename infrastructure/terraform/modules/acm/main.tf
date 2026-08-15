# Region-agnostic on purpose: this module has no opinion about which AWS
# region it runs in — the CALLER selects that by which provider it's
# instantiated with (a plain default-provider call for an ALB's regional
# certificate, or an aliased `providers = { aws = aws.us_east_1 }` call
# for a CloudFront certificate, since CloudFront only ever accepts a cert
# from us-east-1 regardless of which region the distribution itself is
# "in" — CloudFront is a global service). See environments/dev/acm.tf for
# both call sites.
#
# Every resource here is conditional on var.primary_fqdn being set — with
# no domain configured (this repository's actual state today — see the
# Phase 10A report), this module creates nothing and `terraform plan`
# succeeds with an empty plan for it.

locals {
  enabled          = var.primary_fqdn != ""
  zone_id_supplied = var.route53_zone_id != ""
  auto_validate    = local.enabled && local.zone_id_supplied
}

resource "aws_acm_certificate" "this" {
  count = local.enabled ? 1 : 0

  domain_name               = var.primary_fqdn
  subject_alternative_names = var.additional_fqdns
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-${var.cert_purpose}"
  })
}

resource "aws_route53_record" "validation" {
  for_each = local.auto_validate ? {
    for dvo in aws_acm_certificate.this[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : {}

  zone_id         = var.route53_zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  count = local.auto_validate ? 1 : 0

  certificate_arn         = aws_acm_certificate.this[0].arn
  validation_record_fqdns = [for record in aws_route53_record.validation : record.fqdn]
}
