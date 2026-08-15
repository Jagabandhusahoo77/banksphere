output "bucket_ids" {
  value = { for key, bucket in aws_s3_bucket.this : key => bucket.id }
}

output "bucket_arns" {
  value = { for key, bucket in aws_s3_bucket.this : key => bucket.arn }
}

output "bucket_regional_domain_names" {
  description = "Used as the CloudFront origin domain name — see modules/cloudfront."
  value       = { for key, bucket in aws_s3_bucket.this : key => bucket.bucket_regional_domain_name }
}
