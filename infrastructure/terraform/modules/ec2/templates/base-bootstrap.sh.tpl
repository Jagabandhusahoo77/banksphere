#!/bin/bash
# Base node bootstrap — the part every ${project_name} environment's EC2
# instance needs regardless of what runs on it (currently: k3s, see
# modules/k3s). Installs only the CloudWatch Agent (host disk/memory
# metrics — CPU/status checks are free built-in EC2 metrics, no agent
# needed for those). Everything runtime-specific is appended below this
# point via user_data_extra.
set -euo pipefail
exec > >(tee /var/log/banksphere-base-bootstrap.log) 2>&1

echo "== Installing CloudWatch Agent =="
dnf install -y amazon-cloudwatch-agent
mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CWCONFIG'
${cloudwatch_agent_config}
CWCONFIG
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json

echo "== Base bootstrap complete — handing off to runtime-specific bootstrap (if any) =="
