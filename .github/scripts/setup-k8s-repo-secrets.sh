#!/usr/bin/env bash
# setup-k8s-repo-secrets.sh
#
# Creates Kubernetes connection secrets (K8S_SERVER, K8S_CA_CERT, K8S_TOKEN)
# at the GitHub **repository** level.
# Uses the GitHub CLI (gh). Run once when bootstrapping repo-scoped K8S access.
#
# Usage:
#   .github/scripts/setup-k8s-repo-secrets.sh
#
# Prerequisites:
#   - gh CLI installed and authenticated (gh auth login)
#   - kubectl configured with access to the target cluster
#   - openssl installed (for CA cert extraction)
#
# Update this file whenever a new K8S-related repository secret is added to a workflow.

set -euo pipefail

# ─── SHARED HELPERS ──────────────────────────────────────────────────────────

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

set_repo_secret() {
  local name="$1"
  local value="$2"
  gh secret set "$name" --body "$value" --repo "$REPO"
  echo "           Set."
}

# ─── KUBERNETES SETUP ────────────────────────────────────────────────────────
# Sets K8S_SERVER + K8S_CA_CERT + K8S_TOKEN repository secrets and creates the
# deploy service account in the cluster.

setup_k8s() {
  echo "Setting up Kubernetes repository secrets..."
  echo ""

  # --- K8S_SERVER ---
  read -r -p "  [secret] K8S_SERVER (API server URL, e.g. https://192.168.1.100:6443): " k8s_server
  while [ -z "$k8s_server" ]; do
    echo "           This secret is required."
    read -r -p "  [secret] K8S_SERVER: " k8s_server
  done

  set_repo_secret K8S_SERVER "$k8s_server"

  # --- K8S_CA_CERT (auto-fetched from the server) ---
  local host_port
  host_port=$(echo "$k8s_server" | sed -E 's|https?://||; s|/.*||')

  echo "  [secret] K8S_CA_CERT — fetching CA certificate from $host_port ..."
  local ca_b64
  ca_b64=$("$(dirname "$0")/fetch-k8s-ca.sh" "$k8s_server")

  set_repo_secret K8S_CA_CERT "$ca_b64"

  # --- Service account + long-lived token ---
  local sa_name="blitzpay-deploy"
  local sa_ns="kube-system"
  local secret_name="${sa_name}-token"

  echo ""
  echo "  Creating service account $sa_ns/$sa_name ..."

  kubectl get sa "$sa_name" -n "$sa_ns" &>/dev/null || \
    kubectl create sa "$sa_name" -n "$sa_ns"

  kubectl get clusterrolebinding "$sa_name-binding" &>/dev/null || \
    kubectl create clusterrolebinding "$sa_name-binding" \
      --clusterrole=cluster-admin \
      --serviceaccount="$sa_ns:$sa_name"

  # Create a Secret-based long-lived token (works on all K8S versions)
  if ! kubectl get secret "$secret_name" -n "$sa_ns" &>/dev/null; then
    kubectl apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: $secret_name
  namespace: $sa_ns
  annotations:
    kubernetes.io/service-account.name: $sa_name
type: kubernetes.io/service-account-token
EOF
    echo "  Created secret $sa_ns/$secret_name"
    # Wait for the token controller to populate the token
    sleep 2
  fi

  local token
  token=$(kubectl get secret "$secret_name" -n "$sa_ns" -o jsonpath='{.data.token}' | base64 -d)

  if [ -z "$token" ]; then
    echo "  ERROR: token not populated in secret $secret_name"
    exit 1
  fi

  set_repo_secret K8S_TOKEN "$token"
  echo "  [secret] K8S_TOKEN — extracted from secret $sa_ns/$secret_name"
  echo ""
}

# ─── MAIN ────────────────────────────────────────────────────────────────────

echo "Configuring Kubernetes repository secrets for: $REPO"
echo ""

setup_k8s

echo "Done. This repository can now reference these secrets in GitHub Actions."
