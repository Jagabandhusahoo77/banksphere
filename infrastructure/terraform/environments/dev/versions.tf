terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # No backend block — state is local (terraform.tfstate in this
  # directory) for this phase. This is a deliberate, reviewable gap, not
  # an oversight: this environment's state will contain the generated
  # DB_PASSWORD/JWT_SECRET/EMPLOYEE_JWT_SECRET values (see secrets.tf),
  # so local state must not be the final answer for any real/shared use —
  # see docs/deployment/secrets.md and infrastructure/README.md's
  # "before you run terraform apply" checklist for the S3+DynamoDB
  # remote-backend setup this should move to first.
}
