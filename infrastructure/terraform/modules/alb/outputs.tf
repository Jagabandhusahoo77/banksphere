output "dns_name" {
  value = aws_lb.this.dns_name
}

output "zone_id" {
  value = aws_lb.this.zone_id
}

output "arn" {
  value = aws_lb.this.arn
}

output "http_listener_arn" {
  description = "The plain-HTTP listener's ARN — always exists. For the private ALB (no certificate_arn, so https_enabled is false), this is also the listener that actually forwards traffic, and is what modules/api_gateway integrates the VPC Link against."
  value       = aws_lb_listener.http.arn
}

output "https_listener_arn" {
  description = "The HTTPS listener's ARN — only exists once certificate_arn is set (empty string otherwise)."
  value       = local.https_enabled ? aws_lb_listener.https[0].arn : ""
}
