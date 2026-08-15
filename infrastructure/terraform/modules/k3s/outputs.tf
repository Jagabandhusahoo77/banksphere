output "bootstrap_script" {
  description = "Rendered k3s + Argo CD bootstrap script — pass this into modules/ec2's user_data_extra input."
  value       = local.bootstrap_script
}

output "namespace" {
  value = var.namespace
}
