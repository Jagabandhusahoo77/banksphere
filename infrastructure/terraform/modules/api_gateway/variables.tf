variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "private_subnet_ids" {
  description = "Private subnets the VPC Link's own ENIs are provisioned into. At least 2, different AZs — see modules/networking."
  type        = list(string)
}

variable "vpc_link_security_group_id" {
  description = "Security group for the VPC Link's ENIs — see modules/security's vpc_link_security_group_id output."
  type        = string
}

variable "private_alb_listener_arn" {
  description = "The private ALB's listener ARN (HTTP_PROXY integration target) — see modules/alb's http_listener_arn output on the private ALB instantiation."
  type        = string
}

variable "domain_name" {
  description = "api-<env>.<domain> — empty string skips the custom domain entirely (API Gateway still gets its own default execute-api.<region>.amazonaws.com endpoint, always). See docs/deployment/dns-and-https.md's domain_configured pattern."
  type        = string
  default     = ""
}

variable "certificate_arn" {
  description = "Regional ACM certificate ARN covering domain_name — same region as the API, reused from the same certificate the private ALB path would otherwise need. Only read if domain_name is set."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
