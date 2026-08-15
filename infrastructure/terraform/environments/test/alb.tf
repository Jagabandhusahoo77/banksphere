module "alb" {
  source = "../../modules/alb"

  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  public_subnet_ids  = module.networking.public_subnet_ids
  security_group_id  = module.security.alb_security_group_id
  target_instance_id = module.ec2.instance_id
  target_port        = local.app_ports.ingress_http
  certificate_arn    = module.acm_alb.certificate_arn
}
