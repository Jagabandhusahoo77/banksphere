variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "sites" {
  description = "One entry per static site to front with CloudFront (customer-portal, employee-portal). alias is the intended custom hostname (e.g. \"app-dev.example.com\") — leave it \"\" if no domain is configured yet (the distribution is still created, reachable at its own *.cloudfront.net name, just without a custom domain/cert attached)."
  type = map(object({
    bucket_id                   = string
    bucket_arn                  = string
    bucket_regional_domain_name = string
    alias                       = string
  }))
}

variable "certificate_arn" {
  description = "ACM certificate ARN, REQUIRED to be in us-east-1 regardless of the main deployment region (a hard CloudFront requirement — see docs/deployment/dns-and-https.md). Empty string if no domain is configured — distributions then serve over the default *.cloudfront.net certificate instead, with no aliases attached."
  type        = string
  default     = ""
}

variable "price_class" {
  description = "PriceClass_100 (US/Canada/Europe edge locations only) is the cheapest tier that still gives real CDN benefit — see docs/deployment/cost-drivers.md. Use PriceClass_All only if you have real traffic from Asia/South America/Australia to justify the extra cost."
  type        = string
  default     = "PriceClass_100"
}

variable "tags" {
  type    = map(string)
  default = {}
}
