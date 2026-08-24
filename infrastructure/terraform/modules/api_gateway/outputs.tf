output "api_endpoint" {
  description = "The API's own default endpoint (https://<api-id>.execute-api.<region>.amazonaws.com) — always populated, reachable even before any domain is configured."
  value       = aws_apigatewayv2_api.this.api_endpoint
}

output "api_id" {
  value = aws_apigatewayv2_api.this.id
}

output "vpc_link_id" {
  value = aws_apigatewayv2_vpc_link.this.id
}

output "domain_target_domain_name" {
  description = "For a Route53 ALIAS record targeting the custom domain (api-<env>.<domain>) — empty string if no domain is configured. See modules/dns."
  value       = var.domain_name != "" ? aws_apigatewayv2_domain_name.this[0].domain_name_configuration[0].target_domain_name : ""
}

output "domain_hosted_zone_id" {
  description = "Companion to domain_target_domain_name for the same Route53 ALIAS record."
  value       = var.domain_name != "" ? aws_apigatewayv2_domain_name.this[0].domain_name_configuration[0].hosted_zone_id : ""
}
