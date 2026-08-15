output "certificate_arn" {
  description = "Validated certificate ARN, or empty string if primary_fqdn was not set, or if it was set but route53_zone_id was not (in which case the cert exists but is not yet validated — see domain_validation_options below)."
  value       = local.auto_validate ? aws_acm_certificate_validation.this[0].certificate_arn : ""
}

output "certificate_arn_unvalidated" {
  description = "The certificate ARN regardless of validation state — useful if you're validating it outside Terraform (route53_zone_id not supplied)."
  value       = local.enabled ? aws_acm_certificate.this[0].arn : ""
}

output "domain_validation_options" {
  description = "DNS records to create manually if route53_zone_id was not supplied."
  value       = local.enabled ? aws_acm_certificate.this[0].domain_validation_options : []
}
