# BankSphere

A fictional, cloud-native banking platform built to demonstrate modern **software engineering, DevOps, Cloud, Kubernetes, and SRE practices**.

BankSphere is inspired by the breadth of features found in real-world retail banking applications, but it is an entirely original project: no real bank's code, branding, APIs, credentials, or proprietary assets are used. All data, customers, and transactions are fictional.

## What this project demonstrates

- Microservices architecture with independently deployable Spring Boot services
- REST APIs and event-driven communication via Kafka
- Containerization with Docker and orchestration with Kubernetes (Amazon EKS)
- Infrastructure as Code with Terraform on AWS
- GitOps-based continuous delivery with Argo CD
- CI/CD pipelines (GitHub Actions), static analysis (SonarQube), and image scanning (Trivy)
- Full observability stack: metrics, logs, traces, dashboards, alerts, SLOs/SLIs
- SRE practices: reliability patterns, chaos/failure testing, incident runbooks

## Planned application architecture

```text
Customer
   ↓
React (frontend)
   ↓
API Gateway
   ↓
Microservices (auth, customer, account, transaction, payment, beneficiary, card, loan, notification, audit)
   ↓
Kafka
   ↓
PostgreSQL / Redis
```

## Planned infrastructure architecture

```text
AWS
 ├── VPC
 ├── EKS
 ├── RDS
 ├── Redis (ElastiCache)
 ├── ECR
 ├── ALB
 ├── Route 53
 ├── CloudFront
 ├── WAF
 └── CloudWatch
```

## Current status

**Phase 1 vertical slice is implemented:** React frontend → three Spring Boot REST APIs (`customer-service`, `account-service`, `transaction-service`) → PostgreSQL, each with Flyway-managed schemas, Dockerfiles, and a local `docker-compose.yml`. No API Gateway, authentication, Kafka, Redis, or infrastructure-as-code exists yet — see [`docs/architecture/application-architecture.md`](docs/architecture/application-architecture.md) for exactly what does and doesn't exist today, and [`backend/README.md`](backend/README.md) / [`frontend/README.md`](frontend/README.md) for how to run it locally.

## Repository layout

| Directory   | Purpose |
|-------------|---------|
| [`frontend/`](frontend/README.md)   | React + TypeScript customer-facing web application |
| [`backend/`](backend/README.md)    | Spring Boot microservices and shared libraries |
| [`infra/`](infra/README.md)      | Terraform, Kubernetes manifests, and Helm charts |
| [`gitops/`](gitops/README.md)     | Argo CD application definitions (desired cluster state) |
| [`docker/`](docker/README.md)     | Local Docker Compose development environment |
| [`docs/`](docs/)                  | Architecture, API, security, monitoring, and runbook documentation |
| [`scripts/`](scripts/)            | Developer and CI/CD helper scripts |
| [`.github/`](.github/)            | GitHub Actions workflows, issue and PR templates |

## Technology stack

**Frontend:** React, TypeScript, Vite, React Router, Axios, Tailwind CSS
**Backend:** Java, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Redis, Apache Kafka, Maven
**Infrastructure:** AWS (VPC, EKS, RDS, ElastiCache, ECR, ALB, Route 53, CloudFront, WAF, IAM, KMS, Secrets Manager, CloudWatch), Terraform, Docker, Kubernetes, Helm
**DevOps:** Git, GitHub Actions, SonarQube, Trivy, Argo CD
**Observability:** Prometheus, Grafana, OpenTelemetry, Fluent Bit, OpenSearch

## Development phases

This project is built incrementally rather than all at once:

1. Project structure (this task)
2. Frontend + backend foundation
3. PostgreSQL
4. Authentication
5. Account + transaction functionality
6. Microservices
7. Kafka + Redis
8. Docker
9. Kubernetes
10. AWS + Terraform
11. CI/CD
12. GitOps + Argo CD
13. Monitoring, logging, tracing
14. SRE testing + failure scenarios

## Disclaimer

BankSphere is a fictional educational project. It is not affiliated with, endorsed by, or connected to any real bank. No production secrets, credentials, or real customer data should ever be committed to this repository.
