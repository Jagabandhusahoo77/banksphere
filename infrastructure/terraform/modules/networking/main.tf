# Deliberately public-subnets-only — no NAT Gateway, no private subnets.
#
# Why: the only compute this phase provisions is a single EC2 instance per
# environment running Docker Compose (see the ec2 module) plus an ALB in
# front of it (see the security module + environment root). Neither needs
# a private subnet: the EC2 instance's own security group is the actual
# access-control boundary (see modules/security), not subnet placement,
# and putting it in a public subnet with an Internet Gateway route lets it
# reach ECR/apt/yum mirrors directly — a NAT Gateway would cost roughly
# $30-35/month per environment for zero additional security benefit here,
# which contradicts this phase's explicit cost-consciousness requirement.
# Revisit if a future phase adds a resource that genuinely must not have
# a public IP path (e.g. an RDS instance) — see docs/deployment/postgresql.md
# for why Postgres itself stays inside the EC2 instance's own Docker
# network instead, sidestepping this question entirely for now.

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-igw"
  })
}

resource "aws_subnet" "public" {
  count = length(var.public_subnet_cidrs)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index % length(data.aws_availability_zones.available.names)]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-public-${count.index + 1}"
    Tier = "public"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}
