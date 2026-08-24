variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  description = "VPC to create security groups in — from the networking module."
  type        = string
}

variable "app_ports" {
  description = "Ports on the EC2 instance that the ALB is allowed to reach. As of the k3s rework, this is just the k3s node's Traefik ingress HTTP port (Traefik terminates nothing itself — the ALB terminates the ACM cert and forwards plain HTTP; see modules/k3s and modules/alb). Keys are logical names (used only in descriptions), values are the port numbers."
  type        = map(number)
  default = {
    ingress_http = 80
  }
}

variable "enable_ssh" {
  description = "Whether to open port 22 on the EC2 security group at all. Default false — instance access is via AWS Systems Manager Session Manager (through the IAM instance role, see the ec2 module), which needs no open inbound port. Only set true (with a real admin_cidr_blocks) if SSM access is unavailable for some reason."
  type        = bool
  default     = false
}

variable "admin_cidr_blocks" {
  description = "CIDR blocks allowed to SSH to the instance, only used if enable_ssh = true. Never defaults to 0.0.0.0/0 — must be explicitly provided (e.g. your own office/VPN IP as a /32)."
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.admin_cidr_blocks, "0.0.0.0/0")
    error_message = "admin_cidr_blocks must not contain 0.0.0.0/0 — SSH from anywhere on the internet is not permitted. Use your own IP as a /32, or leave enable_ssh = false and use SSM Session Manager instead."
  }
}

variable "alb_ingress_cidr_blocks" {
  description = "CIDR blocks allowed to reach the PUBLIC ALB's listener(s). Defaults to the whole internet, which is the point of a public-facing load balancer — restrict this if the environment should not actually be internet-reachable (e.g. an internal-only TEST environment). Never applies to the private ALB, which only ever accepts traffic from the VPC Link security group, never a CIDR block."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "private_alb_listener_port" {
  description = "Port the private ALB listens on and forwards to the EC2 instance — matches the public ALB's own target_port (Traefik's HTTP ingress port), since both ALBs ultimately hit the same k3s node. See modules/api_gateway for the VPC Link integration that reaches this port."
  type        = number
  default     = 80
}

variable "tags" {
  type    = map(string)
  default = {}
}
