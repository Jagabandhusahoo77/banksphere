output "alb_security_group_id" {
  description = "The PUBLIC (internet-facing) ALB's security group."
  value       = aws_security_group.alb.id
}

output "alb_private_security_group_id" {
  description = "The PRIVATE (internal-only) ALB's security group — never accepts a CIDR-based ingress rule, only from vpc_link_security_group_id."
  value       = aws_security_group.alb_private.id
}

output "vpc_link_security_group_id" {
  description = "The API Gateway VPC Link's own security group — pass this to modules/api_gateway."
  value       = aws_security_group.vpc_link.id
}

output "ec2_security_group_id" {
  value = aws_security_group.ec2.id
}
