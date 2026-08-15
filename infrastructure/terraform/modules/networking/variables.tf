variable "project_name" {
  description = "Short project name used in resource names/tags (e.g. \"banksphere\")."
  type        = string
}

variable "environment" {
  description = "Environment name (e.g. \"dev\", \"test\"). Used in resource names/tags."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for this environment's VPC. Must not overlap with any other BankSphere environment's VPC if they are ever peered."
  type        = string
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the public subnets (minimum 2, in different AZs — required by the Application Load Balancer)."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) >= 2
    error_message = "At least 2 public subnet CIDRs are required (an ALB needs subnets in at least 2 Availability Zones)."
  }
}

variable "tags" {
  description = "Common tags applied to every resource this module creates."
  type        = map(string)
  default     = {}
}
