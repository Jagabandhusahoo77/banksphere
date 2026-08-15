variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "bucket_keys" {
  description = "Logical names of the buckets to create — one per static site (e.g. [\"customer-portal\", \"employee-portal\"]). Each becomes its own bucket, never a shared one, so DEV's customer portal assets and TEST's are never in the same bucket."
  type        = list(string)
}

variable "noncurrent_version_expiration_days" {
  description = "How long to keep a superseded object version (versioning is enabled so a bad deploy can be rolled back) before it's expired — bounds storage cost growth from every CI deploy uploading a fresh build."
  type        = number
  default     = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}
