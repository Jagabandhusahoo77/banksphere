# Shared across DEV and TEST — an ECR repository is a registry, not
# environment-specific infrastructure. This module is invoked exactly
# ONCE (from environments/dev, by convention — see environments/dev/main.tf's
# comment), and the test environment reads the same repositories back via
# a data source (environments/test/data.tf) rather than calling this
# module a second time, which would attempt to create repositories that
# already exist and fail. See ADR-equivalent reasoning in
# infrastructure/README.md.
#
# NOTE: this environment's AWS credentials could not be verified when this
# module was written (see the Phase 10A report) — if these repositories
# already exist outside Terraform's state (created manually or by a prior
# process), running `terraform apply` will fail with "repository already
# exists" rather than adopting them. Run `terraform import` for each
# existing repository first in that case — see infrastructure/README.md.

resource "aws_ecr_repository" "this" {
  for_each = toset(var.repository_names)

  name                 = "${var.project_name}/${each.value}"
  image_tag_mutability = "IMMUTABLE" # never overwrite a tag once pushed — see the task's own "prefer git commit SHA, never latest" requirement

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256" # ECR's default at-rest encryption — no customer-managed KMS key, to avoid the extra per-key cost for a dev/test learning environment
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${each.value}"
  })
}

# Expire untagged images quickly (they're always a build/push mistake or an
# intermediate layer, never something a running environment references by
# digest) and cap tagged-image count so a repository doesn't grow
# unbounded — cost control for image storage, not a security measure.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 3 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 3
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep only the most recent ${var.image_retention_count} tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = var.image_retention_count
        }
        action = { type = "expire" }
      }
    ]
  })
}
