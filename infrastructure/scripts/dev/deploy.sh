#!/bin/bash
# BankSphere DEV deploy script — runs ON the DEV EC2 instance itself,
# either via user-data on first boot (infrastructure/terraform/modules/ec2)
# or via `aws ssm send-command` from the CI pipeline on every subsequent
# deploy (see azure-pipelines.yml's "Deploy DEV" stage). Idempotent: safe
# to re-run with the same or a different image tag. Never run this
# locally against your own machine — it assumes it IS the target instance
# (reads secrets via its own IAM role, writes to /opt/banksphere).
set -euo pipefail

ENVIRONMENT="dev"
PROJECT_NAME="banksphere"
SSM_PATH_PREFIX="/banksphere/${ENVIRONMENT}"
APP_DIR="/opt/${PROJECT_NAME}"
IMAGE_TAG="${1:?Usage: deploy.sh <image-tag>   (the git commit SHA an image was pushed to ECR with)}"

echo "== Resolving region/account from instance metadata (IMDSv2) =="
IMDS_TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
AWS_REGION=$(curl -sS -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" "http://169.254.169.254/latest/meta-data/placement/region")
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "${AWS_REGION}")
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "== Deploying ${PROJECT_NAME} ${ENVIRONMENT} :: image tag ${IMAGE_TAG} in ${AWS_REGION} =="

echo "== Refreshing secrets from SSM Parameter Store (${SSM_PATH_PREFIX}/*) =="
DB_PASSWORD=$(aws ssm get-parameter --name "${SSM_PATH_PREFIX}/DB_PASSWORD" --with-decryption --query Parameter.Value --output text --region "${AWS_REGION}")
JWT_SECRET=$(aws ssm get-parameter --name "${SSM_PATH_PREFIX}/JWT_SECRET" --with-decryption --query Parameter.Value --output text --region "${AWS_REGION}")
EMPLOYEE_JWT_SECRET=$(aws ssm get-parameter --name "${SSM_PATH_PREFIX}/EMPLOYEE_JWT_SECRET" --with-decryption --query Parameter.Value --output text --region "${AWS_REGION}")
# DOMAIN_NAME is only present once a real domain has been configured (see
# infrastructure/terraform/modules/dns) — falls back to empty, which
# still deploys successfully but leaves CORS_ALLOWED_ORIGINS pointing at
# an incomplete hostname until a domain exists. See docs/deployment/dns-and-https.md.
DOMAIN_NAME=$(aws ssm get-parameter --name "${SSM_PATH_PREFIX}/DOMAIN_NAME" --query Parameter.Value --output text --region "${AWS_REGION}" 2>/dev/null || echo "")

mkdir -p "${APP_DIR}"
cat > "${APP_DIR}/.env" <<ENVEOF
ENVIRONMENT=${ENVIRONMENT}
AWS_REGION=${AWS_REGION}
ECR_REGISTRY=${ECR_REGISTRY}
IMAGE_TAG=${IMAGE_TAG}
DB_USERNAME=banksphere
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
EMPLOYEE_JWT_SECRET=${EMPLOYEE_JWT_SECRET}
DOMAIN_NAME=${DOMAIN_NAME}
LOG_GROUP_NAME=/${PROJECT_NAME}/${ENVIRONMENT}/app
ENVEOF
chmod 600 "${APP_DIR}/.env"

echo "== Logging in to ECR =="
aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

cd "${APP_DIR}"
echo "== Pulling images tagged ${IMAGE_TAG} =="
docker compose --env-file .env pull

echo "== Applying =="
docker compose --env-file .env up -d --remove-orphans

echo "== Pruning images untouched for 72h (disk hygiene — does not touch what's currently running) =="
docker image prune -af --filter "until=72h" || true

echo "== Deploy complete =="
docker compose --env-file .env ps
