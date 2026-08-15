# Private buckets for static frontend builds (customer-portal,
# employee-portal). Never publicly readable directly — see
# docs/deployment/frontend-hosting.md: only CloudFront (via Origin Access
# Control) is granted read access, and that grant is a bucket POLICY
# resource created in the environment root (environments/dev/s3.tf),
# not here, since it needs the CloudFront distribution's ARN, which this
# module has no knowledge of (avoids a circular module dependency — see
# that file's own comment).
#
# KYC documents are explicitly NOT stored here — see
# docs/deployment/kyc-storage.md; they stay on Kubernetes
# PersistentVolumes in DEV/TEST this phase.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "this" {
  for_each = toset(var.bucket_keys)

  # Account id suffix for global uniqueness — S3 bucket names are unique
  # across ALL AWS accounts, not just this one.
  bucket = "${var.project_name}-${var.environment}-${each.value}-${data.aws_caller_identity.current.account_id}"

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-${each.value}"
  })
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # no customer-managed KMS key — see docs/deployment/cost-drivers.md's KMS note
    }
  }
}

resource "aws_s3_bucket_versioning" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  versioning_configuration {
    status = "Enabled" # lets a bad frontend deploy be rolled back to the previous build
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    # Applies to every object in the bucket — an empty filter is how the
    # provider expects "no prefix/tag restriction" to be expressed
    # explicitly (an absent filter/prefix is deprecated and now warns).
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_expiration_days
    }

    # Also clean up incomplete multipart uploads (e.g. an interrupted CI
    # sync) — pure hygiene, avoids paying for abandoned partial uploads.
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    object_ownership = "BucketOwnerEnforced" # disables ACLs entirely — access is via bucket policy (the CloudFront OAC grant) only
  }
}
