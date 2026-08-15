# ECR repositories are created HERE ONLY — they are shared across DEV and
# TEST (an image registry is not environment-specific; the CI pipeline
# builds one image per commit and deploys the SAME image to DEV, then
# after smoke tests, to TEST — see azure-pipelines.yml). The test
# environment reads these same repositories back via a data source
# (environments/test/ecr.tf) instead of calling this module a second
# time, which would try to create already-existing repositories and fail.
#
# This is the one place in this environment's Terraform that isn't
# actually "DEV infrastructure" — it's global infrastructure that
# happens to be applied from DEV's state by convention. If DEV is ever
# torn down independently of TEST, do not run `terraform destroy` here
# without first checking whether TEST still depends on these
# repositories (it does, by design).
module "ecr" {
  source = "../../modules/ecr"

  project_name = var.project_name
  tags = {
    Project   = var.project_name
    ManagedBy = "terraform"
    Scope     = "shared-dev-test"
  }
}
