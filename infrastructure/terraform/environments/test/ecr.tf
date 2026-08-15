# TEST does NOT create ECR repositories — they are shared with DEV (an
# image registry is not environment-specific; see environments/dev/ecr.tf's
# own comment for the full reasoning). This is a read-only lookup of the
# same repositories DEV's Terraform state actually owns/creates.
#
# If DEV has not been applied yet, these data sources will fail to
# resolve — apply DEV first. This is intentional: it is not possible for
# TEST to accidentally create a second, divergent set of repositories.
data "aws_ecr_repository" "this" {
  for_each = toset([
    "customer-service",
    "account-service",
    "transaction-service",
    "beneficiary-service",
    "employee-service",
    "kyc-service",
    "customer-portal",
    "employee-portal",
  ])

  name = "${var.project_name}/${each.value}"
}
