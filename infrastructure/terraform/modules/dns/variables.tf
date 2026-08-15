variable "zone_id" {
  description = "Route53 zone to write records into (already resolved by the caller — see environments/dev/dns.tf — either supplied or created there). Empty string skips every record in this module."
  type        = string
  default     = ""
}

variable "api_fqdn" {
  type    = string
  default = ""
}

variable "alb_dns_name" {
  type    = string
  default = ""
}

variable "alb_zone_id" {
  type    = string
  default = ""
}

variable "app_fqdn" {
  type    = string
  default = ""
}

variable "app_cloudfront_domain_name" {
  type    = string
  default = ""
}

variable "ops_fqdn" {
  type    = string
  default = ""
}

variable "ops_cloudfront_domain_name" {
  type    = string
  default = ""
}

# CloudFront's hosted zone id for ALIAS records is a single, fixed,
# well-known AWS constant — the same for every CloudFront distribution in
# every account/region. See
# https://docs.aws.amazon.com/general/latest/gr/cf_region.html.
variable "cloudfront_hosted_zone_id" {
  type    = string
  default = "Z2FDTNDATAQYW2"
}
