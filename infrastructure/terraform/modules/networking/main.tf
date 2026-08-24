# Public subnets (EC2/k3s node, public ALB) + private subnets (private
# ALB, API Gateway VPC Link ENIs only — see modules/api_gateway).
#
# Still deliberately no NAT Gateway: nothing placed in the private
# subnets needs OUTBOUND internet access. The private ALB only receives
# traffic from the VPC Link (inbound) and forwards to the EC2 node's own
# Traefik ingress, which sits in a PUBLIC subnet — that's a normal,
# fully-routable same-VPC hop over the VPC's automatic "local" route,
# not something a NAT Gateway is involved in either direction. VPC Link
# ENIs likewise only relay inbound API Gateway traffic to the private
# ALB; they don't need to reach the internet. So "private" here means
# "no route to the Internet Gateway," not "needs a NAT Gateway" — see
# docs/deployment/networking.md's cost reasoning, unchanged in spirit.
#
# The EC2/k3s node itself stays in a public subnet (unchanged) — see
# docs/deployment/postgresql.md for why Postgres also stays inside that
# same node rather than needing a private-subnet-only datastore.

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

resource "aws_subnet" "private" {
  count = length(var.private_subnet_cidrs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index % length(data.aws_availability_zones.available.names)]
  # No map_public_ip_on_launch — nothing here gets a public IP.

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-private-${count.index + 1}"
    Tier = "private"
  })
}

# No route to the Internet Gateway — that absence is what makes this
# "private." Every route table gets an automatic, unremovable route for
# the VPC's own CIDR block ("local"), which is all the private ALB and
# VPC Link ENIs need to reach the EC2/k3s node in the public subnet.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.project_name}-${var.environment}-private-rt"
  })
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
