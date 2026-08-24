output "alb_dns_name" {
  description = "The PUBLIC ALB's own AWS-generated DNS name — the frontend entry point, reachable immediately after apply even before a domain is configured (over HTTP). Backend/API traffic goes through API Gateway now, not this ALB — see api_gateway_endpoint."
  value       = module.alb.dns_name
}

output "instance_id" {
  value = module.ec2.instance_id
}

output "instance_public_ip" {
  description = "For reference/troubleshooting only — you should not need this for normal access (use SSM Session Manager: `aws ssm start-session --target <instance_id>`) or for application traffic (use the ALB / CloudFront)."
  value       = module.ec2.instance_public_ip
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "fqdns" {
  description = "The app-dev/ops-dev/api-dev hostnames this environment expects to serve, once domain_name is configured."
  value       = { app = local.app_fqdn, ops = local.ops_fqdn, api = local.api_fqdn }
}

output "domain_configured" {
  value = local.domain_configured
}

output "hosted_zone_name_servers" {
  description = "If create_hosted_zone = true, the name servers to configure at your domain registrar. Empty otherwise."
  value       = length(aws_route53_zone.this) > 0 ? aws_route53_zone.this[0].name_servers : []
}

output "alb_private_dns_name" {
  description = "The private ALB's own AWS-generated DNS name. Not internet-reachable — for reference/troubleshooting only (e.g. confirming its listener from inside the VPC)."
  value       = module.alb_private.dns_name
}

output "api_gateway_endpoint" {
  description = "API Gateway's own default endpoint — reachable immediately, even before a domain is configured. This is the real way to reach the backend now, not the ALB directly."
  value       = module.api_gateway.api_endpoint
}

output "cloudwatch_dashboard_name" {
  value = module.monitoring.dashboard_name
}

output "ssm_parameter_path_prefix" {
  value = local.ssm_parameter_path_prefix
}

output "argocd_namespace" {
  description = "Argo CD itself lives in the \"argocd\" namespace on the cluster — access its UI via `kubectl port-forward -n argocd svc/argocd-server 8080:443` over an SSM session (see docs/deployment/argocd.md), never exposed publicly this phase."
  value       = "argocd"
}
