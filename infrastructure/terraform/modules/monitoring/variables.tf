variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "ec2_instance_id" {
  description = "Instance ID to attach EC2-level alarms to (from the ec2 module)."
  type        = string
}

variable "aws_region" {
  description = "AWS region this environment runs in — used only to render dashboard widget links correctly, passed explicitly rather than read via a data source (keeps this module's provider-version requirements minimal)."
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for application container logs. Kept short by default for cost — this is a dev/test environment, not a compliance-retention target."
  type        = number
  default     = 14
}

variable "cpu_alarm_threshold_percent" {
  type    = number
  default = 80
}

variable "disk_alarm_threshold_percent" {
  type    = number
  default = 85
}

variable "memory_alarm_threshold_percent" {
  type    = number
  default = 85
}

variable "alert_email" {
  description = "Email address to subscribe to the alarm SNS topic. Leave empty to skip creating a subscription (the topic is still created, and you can subscribe manually or wire it into a different notification channel later)."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
