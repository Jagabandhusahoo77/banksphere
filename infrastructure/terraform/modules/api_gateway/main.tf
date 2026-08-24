# API Gateway HTTP API + VPC Link — the AWS-documented, current way to
# expose a private ALB through API Gateway (VPC Link v2, used only by
# HTTP APIs, not the older REST API + VPC Link v1 mechanism). See
# docs/deployment/api-gateway.md.
#
# api.<domain> is intended to point HERE (not at any ALB directly) — see
# modules/dns. This module never touches the public ALB; it only ever
# talks to the PRIVATE one, over the VPC Link, matching the target
# architecture's "API Gateway -> VPC Link -> Private ALB -> backend"
# chain exactly.

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "${var.project_name}-${var.environment}"
  subnet_ids         = var.private_subnet_ids
  security_group_ids = [var.vpc_link_security_group_id]

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-vpc-link"
  })
}

resource "aws_apigatewayv2_api" "this" {
  name          = "${var.project_name}-${var.environment}"
  protocol_type = "HTTP"

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-api"
  })
}

# HTTP_PROXY + VPC_LINK connection type — API Gateway forwards the
# request essentially unmodified to the private ALB's own HTTP listener,
# which is what actually enforces routing (all it does, via Traefik on
# the other end — see modules/alb/README-equivalent comment). No request/
# response transformation, no per-route Lambda — this is a thin, direct
# proxy, matching "do not invent resources or configuration" for what is
# fundamentally still the same backend routing this project already has.
resource "aws_apigatewayv2_integration" "private_alb" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = var.private_alb_listener_arn
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.this.id

  payload_format_version = "1.0"
}

# One catch-all route — the private ALB/Traefik/Ingress chain already
# does the real path-based routing to the six backend services (the SAME
# Ingress rules the public path already used — see
# gitops/apps/banksphere/templates/ingress.yaml). API Gateway does not
# need to duplicate that routing table; it only needs to forward
# everything through.
resource "aws_apigatewayv2_route" "proxy" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.private_alb.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-api-stage"
  })
}

# Custom domain (api.<domain>) — conditional on a domain actually being
# configured, same domain_configured gate every other DNS-adjacent
# resource in this project already uses. Reuses the SAME regional ACM
# certificate the private ALB's own module instantiation would otherwise
# need (see environments/dev/acm.tf) — API Gateway regional custom
# domains need a cert in the API's own region, same requirement an ALB
# has, so no second certificate is needed here.
resource "aws_apigatewayv2_domain_name" "this" {
  count = var.domain_name != "" ? 1 : 0

  domain_name = var.domain_name

  domain_name_configuration {
    certificate_arn = var.certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-api-domain"
  })
}

resource "aws_apigatewayv2_api_mapping" "this" {
  count = var.domain_name != "" ? 1 : 0

  api_id      = aws_apigatewayv2_api.this.id
  domain_name = aws_apigatewayv2_domain_name.this[0].id
  stage       = aws_apigatewayv2_stage.default.id
}
