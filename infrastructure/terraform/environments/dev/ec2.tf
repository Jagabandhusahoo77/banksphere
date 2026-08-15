# modules/k3s renders the node's runtime bootstrap (k3s + Argo CD + ECR
# credential refresh + app-secrets provisioning + the Argo CD Application
# registration); modules/ec2 provisions the instance itself and appends
# that rendered script after its own CloudWatch Agent setup. Neither
# module knows about the other's internals — see each module's own
# comment.
module "k3s" {
  source = "../../modules/k3s"

  project_name = var.project_name
  environment  = var.environment
  aws_region   = var.aws_region
  namespace    = "${var.project_name}-${var.environment}" # banksphere-dev — see the task's own required namespace naming

  k3s_version    = var.k3s_version
  argocd_version = var.argocd_version

  gitops_repo_url      = var.gitops_repo_url
  gitops_repo_revision = var.gitops_repo_revision
  gitops_apps_path     = var.gitops_apps_path

  ssm_parameter_path_prefix = local.ssm_parameter_path_prefix
}

module "ec2" {
  source = "../../modules/ec2"

  project_name        = var.project_name
  environment         = var.environment
  aws_region          = var.aws_region
  subnet_id           = module.networking.public_subnet_ids[0]
  security_group_ids  = [module.security.ec2_security_group_id]
  instance_type       = var.instance_type
  root_volume_size_gb = var.root_volume_size_gb

  ecr_repository_arns       = values(module.ecr.repository_arns)
  ssm_parameter_path_prefix = local.ssm_parameter_path_prefix

  cloudwatch_agent_config = file("${path.root}/../../modules/ec2/templates/cloudwatch-agent-config.json.tpl")
  user_data_extra         = module.k3s.bootstrap_script

  tags = local.common_tags
}
