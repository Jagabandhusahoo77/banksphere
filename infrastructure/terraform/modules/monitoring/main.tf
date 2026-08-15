# Deliberately minimal — CloudWatch only (EC2 host health/CPU/disk,
# container logs, basic alarms). No Prometheus/Grafana/OpenTelemetry/full
# observability platform — explicitly out of scope for this phase.

resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.project_name}/${var.environment}/app"
  retention_in_days = var.log_retention_days

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-app-logs"
  })
}

resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-${var.environment}-alerts"

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-alerts"
  })
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alert_email != "" ? 1 : 0

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# EC2 status checks and CPU are built-in EC2 metrics — no agent required.
resource "aws_cloudwatch_metric_alarm" "status_check_failed" {
  alarm_name          = "${var.project_name}-${var.environment}-instance-status-check-failed"
  alarm_description   = "EC2 instance status check failed — the instance may need to be stopped/started or replaced."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = var.ec2_instance_id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "breaching"

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "${var.project_name}-${var.environment}-cpu-high"
  alarm_description   = "EC2 CPU utilization above ${var.cpu_alarm_threshold_percent}% for 10 minutes."
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = var.ec2_instance_id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.cpu_alarm_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"

  tags = var.tags
}

# Disk and memory are NOT built-in EC2 metrics — they require the Amazon
# CloudWatch Agent running on the instance (installed by the ec2 module's
# user-data). These alarms' dimensions must match that agent's config
# exactly (see modules/ec2/templates/cloudwatch-agent-config.json.tpl) —
# if the agent config changes, update these dimensions to match, or the
# alarm will sit permanently in INSUFFICIENT_DATA.
resource "aws_cloudwatch_metric_alarm" "disk_high" {
  alarm_name        = "${var.project_name}-${var.environment}-disk-high"
  alarm_description = "Root volume usage above ${var.disk_alarm_threshold_percent}% — requires the CloudWatch Agent to be running on the instance."
  namespace         = "CWAgent"
  metric_name       = "disk_used_percent"
  dimensions = {
    InstanceId = var.ec2_instance_id
    path       = "/"
    fstype     = "xfs"
  }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.disk_alarm_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching" # notBreaching, not "breaching" — see the comment above; the agent may take a few minutes to report after boot

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "memory_high" {
  alarm_name          = "${var.project_name}-${var.environment}-memory-high"
  alarm_description   = "Memory usage above ${var.memory_alarm_threshold_percent}% — requires the CloudWatch Agent to be running on the instance."
  namespace           = "CWAgent"
  metric_name         = "mem_used_percent"
  dimensions          = { InstanceId = var.ec2_instance_id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.memory_alarm_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  treat_missing_data  = "notBreaching"

  tags = var.tags
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.project_name}-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "EC2 CPU %"
          view    = "timeSeries"
          metrics = [["AWS/EC2", "CPUUtilization", "InstanceId", var.ec2_instance_id]]
          period  = 300
          stat    = "Average"
          region  = var.aws_region
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title = "Disk / Memory %"
          view  = "timeSeries"
          metrics = [
            ["CWAgent", "disk_used_percent", "InstanceId", var.ec2_instance_id, "path", "/", "fstype", "xfs"],
            ["CWAgent", "mem_used_percent", "InstanceId", var.ec2_instance_id]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
        }
      }
    ]
  })
}
