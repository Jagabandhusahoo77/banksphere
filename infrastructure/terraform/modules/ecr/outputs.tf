output "repository_urls" {
  description = "Map of repository name (e.g. \"customer-service\") to its full ECR repository URL — used to build image references in docker-compose.yml and the CI pipeline."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "repository_arns" {
  description = "Map of repository name to ARN — used to scope the EC2 instance role's ECR pull policy to exactly these repositories."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.arn }
}

output "registry_id" {
  description = "The AWS account ID that owns these repositories (all repositories share one registry per account/region)."
  value       = length(aws_ecr_repository.this) > 0 ? values(aws_ecr_repository.this)[0].registry_id : null
}
