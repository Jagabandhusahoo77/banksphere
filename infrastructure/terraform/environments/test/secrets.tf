# Generated once by Terraform, never typed by a human, never committed
# anywhere — stored as SecureString SSM Parameter Store entries (see
# docs/deployment/secrets.md for why SSM Parameter Store rather than AWS
# Secrets Manager was chosen: cost). The EC2 instance role can read
# exactly this environment's path (/banksphere/test/*) and nothing under
# /banksphere/test/* — see modules/ec2's IAM policy.
#
# KNOWN TRADE-OFF: these values ARE present in this environment's
# Terraform state (Terraform must know a resource's value to manage it).
# Local state (see versions.tf) means that file is the actual secret
# store today — treat terraform.tfstate as sensitive, and move to an
# encrypted remote backend (S3 + SSE-KMS + DynamoDB locking) before any
# real/shared use. This is called out explicitly rather than left for
# someone to discover later.

resource "random_password" "db" {
  length  = 32
  special = false # Postgres connection strings/env vars handle special characters fine, but this avoids any shell-quoting surprises in deploy.sh/.env parsing
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "random_password" "employee_jwt_secret" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.ssm_parameter_path_prefix}DB_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
  tags  = local.common_tags
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_parameter_path_prefix}JWT_SECRET"
  type  = "SecureString"
  value = random_password.jwt_secret.result
  tags  = local.common_tags
}

resource "aws_ssm_parameter" "employee_jwt_secret" {
  name  = "${local.ssm_parameter_path_prefix}EMPLOYEE_JWT_SECRET"
  type  = "SecureString"
  value = random_password.employee_jwt_secret.result
  tags  = local.common_tags
}

# Only created once a real domain is configured — deploy.sh tolerates
# this parameter not existing yet (see its own comment).
resource "aws_ssm_parameter" "domain_name" {
  count = var.domain_name != "" ? 1 : 0

  name  = "${local.ssm_parameter_path_prefix}DOMAIN_NAME"
  type  = "String"
  value = var.domain_name
  tags  = local.common_tags
}
