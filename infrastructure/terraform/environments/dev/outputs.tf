output "alb_dns_name" {
  description = "ALB's own AWS-generated DNS name — the api-dev ingress path, reachable immediately after apply even before a domain is configured (over HTTP)."
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

output "cloudfront_domain_names" {
  value = module.cloudfront.distribution_domain_names
}

output "cloudfront_distribution_ids" {
  description = "Feed these into the pipeline's CLOUDFRONT_DIST_ID_APP_DEV/CLOUDFRONT_DIST_ID_OPS_DEV variables — azure-pipelines.yml's frontend deploy stage invalidates by distribution id, not domain name."
  value       = module.cloudfront.distribution_ids
}

output "s3_bucket_ids" {
  value = module.s3.bucket_ids
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
