module "monitoring" {
  source = "../../modules/monitoring"

  project_name    = var.project_name
  environment     = var.environment
  aws_region      = var.aws_region
  ec2_instance_id = module.ec2.instance_id
  alert_email     = var.alert_email
  tags            = local.common_tags
}
