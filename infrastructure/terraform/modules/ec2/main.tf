data "aws_caller_identity" "current" {}

data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ---------------------------------------------------------------------
# IAM — the instance authenticates to AWS via this role (instance
# profile), never long-lived access keys. Scoped to exactly what this
# environment's instance needs: pull its own repos from ECR, read its own
# environment's secrets from SSM (not another environment's), and the two
# AWS-managed policies for SSM Session Manager (shell access with no open
# port 22) and the CloudWatch Agent (host disk/memory metrics + Docker's
# awslogs log driver, which reuses this same instance role automatically).
# ---------------------------------------------------------------------

resource "aws_iam_role" "this" {
  name = "${var.project_name}-${var.environment}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-ec2-role"
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_role_policy" "app_access" {
  name = "${var.project_name}-${var.environment}-app-access"
  role = aws_iam_role.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*" # this specific action does not support resource-level scoping — AWS requirement, not a broadening choice
      },
      {
        Sid    = "EcrPull"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
        ]
        Resource = length(var.ecr_repository_arns) > 0 ? var.ecr_repository_arns : ["arn:aws:ecr:${var.aws_region}:${data.aws_caller_identity.current.account_id}:repository/${var.project_name}/*"]
      },
      {
        Sid    = "ReadOwnEnvironmentSecrets"
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
        ]
        # Scoped to THIS environment's path only — a dev instance's role
        # cannot read /banksphere/test/* and vice versa, enforcing the
        # "must not share secrets" requirement at the IAM level, not just
        # by convention.
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_path_prefix}*"
      },
      {
        Sid       = "DecryptSecureStringViaSsm"
        Effect    = "Allow"
        Action    = ["kms:Decrypt"]
        Resource  = "*"
        Condition = { StringEquals = { "kms:ViaService" = "ssm.${var.aws_region}.amazonaws.com" } }
      },
    ]
  })
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.project_name}-${var.environment}-ec2-profile"
  role = aws_iam_role.this.name
}

# ---------------------------------------------------------------------
# The instance itself
# ---------------------------------------------------------------------

resource "aws_instance" "this" {
  ami                         = data.aws_ami.amazon_linux_2023.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = var.security_group_ids
  iam_instance_profile        = aws_iam_instance_profile.this.name
  associate_public_ip_address = true # no NAT Gateway — see the networking module's comment; this is the instance's only path to the internet (for ECR/OS updates) and is not itself an inbound-access grant, that's what the security group controls

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gb
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens   = "required" # IMDSv2 only — IMDSv1 is a known SSRF-to-credential-theft vector
    http_endpoint = "enabled"
  }

  # Base (CloudWatch Agent) + runtime-specific (k3s/Argo CD, from
  # modules/k3s's rendered output — see the environment root) bootstrap,
  # concatenated. This module knows nothing about k3s itself, only that
  # it appends whatever string it's handed after its own base setup —
  # see user_data_extra's own description.
  user_data = "${templatefile("${path.module}/templates/base-bootstrap.sh.tpl", {
    project_name            = var.project_name
    cloudwatch_agent_config = var.cloudwatch_agent_config
  })}\n${var.user_data_extra}"

  # Changing the GitOps bootstrap should be rolled out by re-registering
  # the Argo CD Application (or simply letting Argo CD's own reconcile
  # loop pick up a Git change — that's the whole point of GitOps), not by
  # replacing the instance — user_data only actually re-runs on a fresh
  # boot anyway, so treating every content change as "replace the
  # instance" would be both disruptive and not even accomplish the goal
  # unless the instance is also rebooted.
  user_data_replace_on_change = false

  # Likewise, don't replace the instance every time a newer Amazon Linux
  # 2023 AMI is published — data.aws_ami's "most_recent" lookup will
  # resolve to a new id on essentially every `plan` otherwise. Rebuilding
  # on a new AMI should be a deliberate action, not an automatic side
  # effect of running `terraform plan`.
  lifecycle {
    ignore_changes = [ami]
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}"
  })
}
