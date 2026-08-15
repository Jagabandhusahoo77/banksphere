# Declares that this module accepts an explicit provider from its
# caller (rather than always using the caller's default aws provider) —
# required so environments/dev/acm.tf can instantiate this module twice,
# once against the default (regional) provider and once against an
# aws.us_east_1 alias for the CloudFront certificate. See main.tf's own
# comment.
terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws]
    }
  }
}
