# A generic ALB module, instantiated TWICE by the environment root (see
# environments/dev/alb.tf):
#   - PUBLIC (internal=false, in public subnets): the only internet-
#     facing load balancer, forwards to the k3s node's Traefik ingress,
#     which routes to the frontend Kubernetes Service.
#   - PRIVATE (internal=true, in private subnets): never internet-facing,
#     receives traffic only from the API Gateway VPC Link (see
#     modules/api_gateway and modules/security's alb_private/vpc_link
#     security groups), also forwards to Traefik — which routes THIS
#     traffic to the backend Kubernetes Services via the SAME existing
#     Ingress path-prefix rules the public path already used. See
#     docs/deployment/ingress.md.
#
# Both instantiations target the SAME single EC2/k3s node, same port —
# Traefik (not this module, not two different target groups) is what
# actually separates frontend vs. backend traffic, by path. This is
# deliberately the smallest change that reuses the existing ALB->
# instance-target->Traefik->Ingress mechanism rather than inventing a
# second one.

resource "aws_lb" "this" {
  name               = "${var.project_name}-${var.environment}${var.name_suffix}"
  internal           = var.internal
  load_balancer_type = "application"
  security_groups    = [var.security_group_id]
  subnets            = var.subnet_ids

  enable_deletion_protection = false # a dev/test learning environment should stay easy to tear down

  tags = local.local_tags
}

locals {
  local_tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}${var.name_suffix}-alb"
  })
  https_enabled = var.certificate_arn != ""
}

resource "aws_lb_target_group" "ingress" {
  name        = "${var.project_name}-${var.environment}${var.name_suffix}-ingress"
  port        = var.target_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "instance"

  health_check {
    # Routed through Traefik like any other path — the Helm chart's
    # Ingress sends /actuator/health to customer-service (a real,
    # already-public endpoint — see SecurityConfig.permitAll on that
    # path), not a Traefik-internal ping mechanism. Any one of the six
    # backend services would do equally well as the health target; this
    # one was picked arbitrarily. See docs/deployment/ingress.md.
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }

  tags = local.local_tags
}

resource "aws_lb_target_group_attachment" "ingress" {
  target_group_arn = aws_lb_target_group.ingress.arn
  target_id        = var.target_instance_id
  port             = var.target_port
}

resource "aws_lb_listener" "https" {
  count = local.https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ingress.arn
  }

  tags = local.local_tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = local.https_enabled ? [1] : []
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }

  dynamic "default_action" {
    for_each = local.https_enabled ? [] : [1]
    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.ingress.arn
    }
  }

  tags = local.local_tags
}
