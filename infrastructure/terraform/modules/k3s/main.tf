# This module has no AWS resources of its own — it is purely a typed
# wrapper around rendering modules/k3s/templates/bootstrap.sh.tpl into a
# single string, so the environment root and modules/ec2 don't need to
# know anything about k3s/Argo CD specifics. See modules/ec2's own
# comment for how its output is actually used (appended to the EC2
# instance's user-data, after the CloudWatch Agent install step that
# module still owns itself).

locals {
  bootstrap_script = templatefile("${path.module}/templates/bootstrap.sh.tpl", {
    project_name              = var.project_name
    environment               = var.environment
    aws_region                = var.aws_region
    namespace                 = var.namespace
    k3s_version               = var.k3s_version
    argocd_version            = var.argocd_version
    gitops_repo_url           = var.gitops_repo_url
    gitops_repo_revision      = var.gitops_repo_revision
    gitops_apps_path          = var.gitops_apps_path
    ecr_secret_name           = var.ecr_secret_name
    ecr_secret_refresh_hours  = var.ecr_secret_refresh_hours
    ssm_parameter_path_prefix = var.ssm_parameter_path_prefix
    app_secret_name           = var.app_secret_name
  })
}
