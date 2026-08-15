# Terraform

AWS infrastructure for BankSphere, managed as code.

## Structure

```text
terraform/
├── environments/   Per-environment root configurations (dev, staging, prod)
└── modules/        Reusable Terraform modules, one per AWS resource area
```

## Modules

| Module | Purpose |
|---|---|
| `vpc` | Networking: VPC, subnets, route tables, NAT/IGW |
| `eks` | Amazon EKS cluster and node groups |
| `rds` | Amazon RDS (PostgreSQL) |
| `redis` | ElastiCache for Redis |
| `ecr` | Amazon ECR repositories for container images |
| `iam` | IAM roles and policies (least privilege) |
| `s3` | S3 buckets (e.g. state, static assets, backups) |
| `alb` | Application Load Balancer |
| `route53` | DNS zones and records |
| `cloudfront` | CDN distribution |
| `waf` | AWS WAF rules |
| `kms` | Encryption keys |
| `secrets-manager` | Secrets Manager configuration |

Each environment under `environments/` composes these modules with environment-specific variables. No AWS resources have been provisioned yet — modules and environments currently contain no Terraform configuration.

**No AWS credentials or state files are stored in this repository.**
