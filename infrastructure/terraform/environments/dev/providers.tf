provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

# The us-east-1-only provider alias this file used to have (required by
# CloudFront's certificate region rule) was removed along with
# modules/cloudfront — nothing in this environment needs a second AWS
# provider region anymore. See docs/deployment/frontend-hosting.md's
# replacement note and the architecture-change report for why.
