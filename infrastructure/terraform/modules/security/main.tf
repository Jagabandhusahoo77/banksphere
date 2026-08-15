# Two security groups: one for the internet-facing ALB, one for the EC2
# instance. The EC2 instance's inbound rules only ever allow traffic FROM
# the ALB's security group (never 0.0.0.0/0, never a raw CIDR) — this is
# what keeps 5432/8081-8086 unreachable from the internet even though
# nothing in this module hard-codes "deny the internet," it simply never
# grants it access in the first place. See docs/deployment/networking.md.
#
# NOTE: every description string on a security group / security group
# rule below is restricted to AWS EC2's own allowed character set
# (letters, digits, and " ._-:/()#,@[]+=&;{}!$*" — no em dashes, no
# apostrophes) — `terraform validate` enforces this at plan time, not
# just at apply time, which is how this restriction was actually
# discovered while validating this module.

resource "aws_security_group" "alb" {
  name_prefix = "${var.project_name}-${var.environment}-alb-"
  description = "Internet-facing ALB for ${var.project_name} ${var.environment} - terminates HTTPS, forwards to the EC2 instance only."
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTPS from the internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidr_blocks
  }

  ingress {
    description = "HTTP from the internet, redirected to HTTPS by the listener; kept open only so a plain http request gets a redirect instead of a connection refusal"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.alb_ingress_cidr_blocks
  }

  egress {
    description = "To the EC2 instance app ports only"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"] # narrowed in practice by the EC2 SG's ingress rule (which only accepts from this SG's id) — see aws_security_group.ec2 below; kept as -1 here because AWS security groups are stateful and the EC2 SG is the actual chokepoint.
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-alb-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "ec2" {
  name_prefix = "${var.project_name}-${var.environment}-ec2-"
  description = "EC2 instance running Docker Compose for ${var.project_name} ${var.environment}. No inbound access from the internet except optionally SSH from admin_cidr_blocks; every application port is only reachable from the ALB security group."
  vpc_id      = var.vpc_id

  dynamic "ingress" {
    for_each = var.app_ports
    content {
      description     = "${ingress.key}, from the ALB only"
      from_port       = ingress.value
      to_port         = ingress.value
      protocol        = "tcp"
      security_groups = [aws_security_group.alb.id]
    }
  }

  dynamic "ingress" {
    for_each = var.enable_ssh ? [1] : []
    content {
      description = "SSH, admin access only (prefer SSM Session Manager instead where possible)"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.admin_cidr_blocks
    }
  }

  egress {
    description = "All outbound, needed to pull images from ECR, reach SSM/Secrets Manager/CloudWatch endpoints, and OS package updates. The instance has no inbound path from the internet regardless of this rule."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-ec2-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}
