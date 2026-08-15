module "cloudfront" {
  source = "../../modules/cloudfront"

  project_name = var.project_name
  environment  = var.environment

  sites = {
    "customer-portal" = {
      bucket_id                   = module.s3.bucket_ids["customer-portal"]
      bucket_arn                  = module.s3.bucket_arns["customer-portal"]
      bucket_regional_domain_name = module.s3.bucket_regional_domain_names["customer-portal"]
      alias                       = local.app_fqdn
    }
    "employee-portal" = {
      bucket_id                   = module.s3.bucket_ids["employee-portal"]
      bucket_arn                  = module.s3.bucket_arns["employee-portal"]
      bucket_regional_domain_name = module.s3.bucket_regional_domain_names["employee-portal"]
      alias                       = local.ops_fqdn
    }
  }

  certificate_arn = module.acm_cloudfront.certificate_arn
  tags            = local.common_tags
}
