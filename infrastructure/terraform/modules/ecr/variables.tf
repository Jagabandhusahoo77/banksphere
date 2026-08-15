variable "project_name" {
  type = string
}

variable "repository_names" {
  description = "Image repository names to create, matching the actual deployable units in this repository — see backend/README.md and frontend/README.md. Do not add a repository for a service that doesn't exist yet (e.g. payment-service, card-service)."
  type        = list(string)
  default = [
    "customer-service",
    "account-service",
    "transaction-service",
    "beneficiary-service",
    "employee-service",
    "kyc-service",
    "customer-portal",
    "employee-portal",
  ]
}

variable "image_retention_count" {
  description = "How many most-recent tagged images to keep per repository before an AWS ECR lifecycle policy expires older ones. Untagged images are always expired quickly regardless (see the lifecycle policy in main.tf) — this only bounds tagged (commit-SHA) image growth/cost."
  type        = number
  default     = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}
