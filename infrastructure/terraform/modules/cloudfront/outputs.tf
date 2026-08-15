output "distribution_domain_names" {
  description = "The *.cloudfront.net domain name per site — used by modules/dns to create the app-<env>/ops-<env> ALIAS records."
  value       = { for key, dist in aws_cloudfront_distribution.this : key => dist.domain_name }
}

output "distribution_ids" {
  value = { for key, dist in aws_cloudfront_distribution.this : key => dist.id }
}

output "distribution_arns" {
  value = { for key, dist in aws_cloudfront_distribution.this : key => dist.arn }
}
