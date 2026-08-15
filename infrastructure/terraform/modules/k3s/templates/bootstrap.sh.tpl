#!/bin/bash
# k3s + Argo CD node bootstrap for ${project_name} ${environment}.
#
# Runs ONCE, as EC2 user-data, on first boot. Everything after step 7
# (the Argo CD Application registration) is GitOps from here on — this
# script never applies the application's own Deployments/Services/Ingress
# directly, and never runs again after this boot. A subsequent code
# change reaches this cluster by: CI pushes an image -> CI updates the
# image tag in the GitOps repo -> commits -> Argo CD notices the Git
# change and reconciles it. See docs/deployment/gitops.md.
set -euo pipefail
exec > >(tee /var/log/banksphere-k3s-bootstrap.log) 2>&1

NAMESPACE="${namespace}"
ECR_SECRET_NAME="${ecr_secret_name}"

echo "== [1/8] Installing k3s ${k3s_version} (single-node, built-in Traefik ingress + local-path storage) =="
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="${k3s_version}" sh -s - server \
  --write-kubeconfig-mode 644

echo "== Waiting for the node to be Ready =="
for i in $(seq 1 30); do
  if k3s kubectl get nodes 2>/dev/null | grep -q " Ready"; then break; fi
  sleep 5
done
k3s kubectl get nodes

echo "== [2/8] Configuring node access — kubectl wrapper for k3s's bundled one =="
ln -sf /usr/local/bin/k3s /usr/local/bin/kubectl 2>/dev/null || true
mkdir -p /root/.kube
cp /etc/rancher/k3s/k3s.yaml /root/.kube/config
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

echo "== [3/8] Installing kubectl/helm CLIs (for operator troubleshooting only — GitOps/Argo CD is the actual deploy path, see step 7) =="
curl -sfL https://get.helm.sh/helm-v3.21.4-linux-amd64.tar.gz -o /tmp/helm.tar.gz
tar -xzf /tmp/helm.tar.gz -C /tmp
install -m 0755 /tmp/linux-amd64/helm /usr/local/bin/helm-cli # not "helm" — avoids shadowing k3s's own binary named /usr/local/bin/k3s if a future step needs to disambiguate

echo "== [4/8] Preparing the cluster: namespace $NAMESPACE =="
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "== [5/8] ECR access: writing the credential-refresh script + systemd timer (no static AWS keys — uses this node's own IAM role via IMDS) =="
mkdir -p /opt/${project_name}
cat > /opt/${project_name}/refresh-ecr-secret.sh <<'REFRESH_EOF'
#!/bin/bash
set -euo pipefail
NAMESPACE="${namespace}"
ECR_SECRET_NAME="${ecr_secret_name}"
IMDS_TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
AWS_REGION=$(curl -sS -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" "http://169.254.169.254/latest/meta-data/placement/region")
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "$AWS_REGION")
ECR_REGISTRY="$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
TOKEN=$(aws ecr get-login-password --region "$AWS_REGION")
kubectl create secret docker-registry "$ECR_SECRET_NAME" \
  --namespace "$NAMESPACE" \
  --docker-server="$ECR_REGISTRY" \
  --docker-username=AWS \
  --docker-password="$TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
echo "ECR pull secret refreshed in $NAMESPACE at $(date -u)"
REFRESH_EOF
chmod +x /opt/${project_name}/refresh-ecr-secret.sh
/opt/${project_name}/refresh-ecr-secret.sh

cat > /etc/systemd/system/ecr-secret-refresh.service <<'UNIT_EOF'
[Unit]
Description=Refresh the ECR image pull secret for ${project_name} ${environment}
[Service]
Type=oneshot
ExecStart=/opt/${project_name}/refresh-ecr-secret.sh
UNIT_EOF

cat > /etc/systemd/system/ecr-secret-refresh.timer <<'TIMER_EOF'
[Unit]
Description=Run ecr-secret-refresh every ${ecr_secret_refresh_hours}h
[Timer]
OnBootSec=5min
OnUnitActiveSec=${ecr_secret_refresh_hours}h
[Install]
WantedBy=timers.target
TIMER_EOF
systemctl daemon-reload
systemctl enable --now ecr-secret-refresh.timer

echo "== [5b/8] Provisioning app secrets from SSM (${ssm_parameter_path_prefix}*) into the Kubernetes Secret ${app_secret_name} — one-time, unlike the refreshed ECR credential above, since these values don't expire =="
IMDS_TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
AWS_REGION=$(curl -sS -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" "http://169.254.169.254/latest/meta-data/placement/region")
DB_PASSWORD=$(aws ssm get-parameter --name "${ssm_parameter_path_prefix}DB_PASSWORD" --with-decryption --query Parameter.Value --output text --region "$AWS_REGION")
JWT_SECRET=$(aws ssm get-parameter --name "${ssm_parameter_path_prefix}JWT_SECRET" --with-decryption --query Parameter.Value --output text --region "$AWS_REGION")
EMPLOYEE_JWT_SECRET=$(aws ssm get-parameter --name "${ssm_parameter_path_prefix}EMPLOYEE_JWT_SECRET" --with-decryption --query Parameter.Value --output text --region "$AWS_REGION")
kubectl create secret generic "${app_secret_name}" \
  --namespace "$NAMESPACE" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=EMPLOYEE_JWT_SECRET="$EMPLOYEE_JWT_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -
unset DB_PASSWORD JWT_SECRET EMPLOYEE_JWT_SECRET

echo "== [6/8] Installing Argo CD ${argocd_version} =="
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f "https://raw.githubusercontent.com/argoproj/argo-cd/${argocd_version}/manifests/install.yaml"

echo "== Waiting for the Argo CD API server to be available =="
kubectl -n argocd wait --for=condition=available --timeout=300s deployment/argocd-server || true

echo "== [7/8] Registering the GitOps Application (the ONLY app-level kubectl apply this script ever does — everything downstream is Argo CD reconciling from Git, not this script) =="
cat <<APP_EOF | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ${project_name}-${environment}
  namespace: argocd
spec:
  project: default
  source:
    repoURL: ${gitops_repo_url}
    targetRevision: ${gitops_repo_revision}
    path: ${gitops_apps_path}
    helm:
      valueFiles:
        - values.yaml
        - values-${environment}.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: $NAMESPACE
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
APP_EOF

echo "== [8/8] Bootstrap complete. Argo CD will sync $NAMESPACE from ${gitops_repo_url}@${gitops_repo_revision}:${gitops_apps_path} on its own polling loop (default: every 3 minutes) or on webhook if configured. =="
kubectl -n argocd get applications.argoproj.io "${project_name}-${environment}" -o wide || true
