provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

# CloudFront only ever accepts an ACM certificate from us-east-1,
# regardless of which region the rest of this environment runs in — a
# hard AWS requirement, not a choice. Used only by acm.tf's cloudfront
# certificate instance. See docs/deployment/dns-and-https.md.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = local.common_tags
  }
}
