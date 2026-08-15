module "s3" {
  source = "../../modules/s3"

  project_name = var.project_name
  environment  = var.environment
  bucket_keys  = ["customer-portal", "employee-portal"]
  tags         = local.common_tags
}
