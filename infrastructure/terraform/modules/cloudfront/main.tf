# One CloudFront distribution + Origin Access Control per static site.
# The S3 bucket itself is never publicly readable (see modules/s3) — OAC
# is what lets CloudFront read it while the bucket stays fully private;
# this module owns the bucket POLICY resource that grants that (even
# though the bucket resource itself lives in modules/s3) specifically to
# avoid a circular module dependency: the policy needs this module's own
# distribution ARN, so it has to be created here, after the distribution,
# not in modules/s3 which has no knowledge of CloudFront at all.
#
# SPA routing: a client-side-routed React app needs any deep-link
# (e.g. /accounts/123) to still serve index.html (React Router then reads
# the real URL from the browser) — otherwise S3 returns 403/404 for a
# path that isn't a real object. custom_error_response below rewrites
# both to a 200 index.html response.

resource "aws_cloudfront_origin_access_control" "this" {
  for_each = var.sites

  name                              = "${var.project_name}-${var.environment}-${each.key}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "this" {
  for_each = var.sites

  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = var.price_class
  aliases             = var.certificate_arn != "" && each.value.alias != "" ? [each.value.alias] : []
  comment             = "${var.project_name} ${var.environment} ${each.key}"

  origin {
    domain_name              = each.value.bucket_regional_domain_name
    origin_id                = each.key
    origin_access_control_id = aws_cloudfront_origin_access_control.this[each.key].id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = each.key
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  dynamic "viewer_certificate" {
    for_each = var.certificate_arn != "" && each.value.alias != "" ? [1] : []
    content {
      acm_certificate_arn      = var.certificate_arn
      ssl_support_method       = "sni-only"
      minimum_protocol_version = "TLSv1.2_2021"
    }
  }

  dynamic "viewer_certificate" {
    for_each = var.certificate_arn != "" && each.value.alias != "" ? [] : [1]
    content {
      cloudfront_default_certificate = true
    }
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-${each.key}"
  })
}

resource "aws_s3_bucket_policy" "cloudfront_read" {
  for_each = var.sites

  bucket = each.value.bucket_id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipalReadOnly"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${each.value.bucket_arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.this[each.key].arn
        }
      }
    }]
  })
}
