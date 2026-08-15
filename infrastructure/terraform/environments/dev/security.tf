module "security" {
  source = "../../modules/security"

  project_name      = var.project_name
  environment       = var.environment
  vpc_id            = module.networking.vpc_id
  app_ports         = local.app_ports
  enable_ssh        = var.enable_ssh
  admin_cidr_blocks = var.admin_cidr_blocks
  tags              = local.common_tags
}
