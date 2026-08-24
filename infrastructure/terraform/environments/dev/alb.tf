# PUBLIC — the only internet-facing ALB. Frontend entry point (see
# target architecture): app-dev.<domain> -> this -> the k3s node's
# Traefik -> the frontend Kubernetes Service. Unchanged resource
# identity from before this architecture change (same name, no
# name_suffix) — only its certificate now covers app_fqdn instead of
# api_fqdn, since API traffic no longer comes through this ALB at all.
module "alb" {
  source = "../../modules/alb"

  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  subnet_ids         = module.networking.public_subnet_ids
  internal           = false
  security_group_id  = module.security.alb_security_group_id
  target_instance_id = module.ec2.instance_id
  target_port        = local.app_ports.ingress_http
  certificate_arn    = module.acm_public_alb.certificate_arn
}

# PRIVATE — never internet-facing. Reached only via the API Gateway VPC
# Link (see api_gateway.tf and modules/security's alb_private/vpc_link
# security groups). No certificate_arn: TLS terminates at API Gateway,
# not here — see acm.tf's own comment. Targets the SAME EC2/k3s node as
# the public ALB; Traefik's existing Ingress path rules are what
# actually route this traffic to the backend services, unchanged.
module "alb_private" {
  source = "../../modules/alb"

  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  subnet_ids         = module.networking.private_subnet_ids
  internal           = true
  name_suffix        = "-private"
  security_group_id  = module.security.alb_private_security_group_id
  target_instance_id = module.ec2.instance_id
  target_port        = local.app_ports.ingress_http
}
