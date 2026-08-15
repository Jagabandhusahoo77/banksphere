# A single ALB whose only job is: terminate the ACM certificate for
# api-<env>.<domain> and forward plain HTTP to the k3s node's Traefik
# ingress controller, which then does the REAL routing (by Host header,
# to the right Kubernetes Service) using the Ingress resources the Helm
# chart creates — see gitops/apps/banksphere/templates/ingress.yaml and
# docs/deployment/ingress.md.
#
# app-<env>.<domain>/ops-<env>.<domain> do NOT go through this ALB at all
# — they're served by CloudFront (see modules/cloudfront) pointed
# directly at S3. This ALB exists purely for the Kubernetes API traffic
# path, which is why it needs only ONE target group and no host-header
# listener rules (there's only one thing it's ever routing to).

resource "aws_lb" "this" {
  name               = "${var.project_name}-${var.environment}"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.security_group_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = false # a dev/test learning environment should stay easy to tear down

  tags = local.local_tags
}

locals {
  local_tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-alb"
  })
  https_enabled = var.certificate_arn != ""
}

resource "aws_lb_target_group" "ingress" {
  name        = "${var.project_name}-${var.environment}-ingress"
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
