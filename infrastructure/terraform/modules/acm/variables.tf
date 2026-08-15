variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "cert_purpose" {
  description = "Short label for this cert's use (e.g. \"alb\" or \"cloudfront\") — used only in the resource Name tag, to tell the two certs apart in the AWS console when a single environment requests both."
  type        = string
}

variable "primary_fqdn" {
  description = "Primary hostname (the certificate's CN), e.g. \"api-dev.example.com\". Leave empty to skip creating any certificate at all — every resource in this module is conditional on this, same pattern as modules/dns. See docs/deployment/dns-and-https.md."
  type        = string
  default     = ""
}

variable "additional_fqdns" {
  description = "Extra SANs on the same certificate, e.g. [\"ops-dev.example.com\"] when app-dev and ops-dev share one CloudFront-facing cert."
  type        = list(string)
  default     = []
}

variable "route53_zone_id" {
  description = "Zone to write DNS validation records into. Required if primary_fqdn is set and you want Terraform to validate the certificate automatically; if empty, the certificate is still requested but stays PENDING_VALIDATION until you create the validation records yourself (see outputs.domain_validation_options)."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
