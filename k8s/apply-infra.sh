#!/usr/bin/env bash
# Applies k8s/ infra manifests with their ${...} hostnames filled in from the
# blitzpay-domains ConfigMap, which the blitz-pay Helm chart derives from
# global.baseDomain in helm/blitz-pay/values-<env>.yaml.
#
# Usage: k8s/apply-infra.sh [namespace] [manifest...]
#   k8s/apply-infra.sh                                  # all templated manifests, blitzpay-staging
#   k8s/apply-infra.sh blitzpay-staging k8s/minio.yml   # one manifest
#   DRY_RUN=1 k8s/apply-infra.sh                        # print rendered YAML only
set -euo pipefail

cd "$(dirname "$0")/.."
namespace="${1:-blitzpay-staging}"
shift || true
manifests=("$@")
[ ${#manifests[@]} -gt 0 ] || manifests=(k8s/infra-ingresses.yml k8s/minio.yml)

domains=$(kubectl get configmap blitzpay-domains -n "$namespace" -o json) || {
  echo "blitzpay-domains ConfigMap not found in $namespace — deploy the blitz-pay Helm chart first." >&2
  exit 1
}

sed_args=()
for key in $(jq -r '.data | keys[]' <<<"$domains"); do
  sed_args+=(-e "s|\${$key}|$(jq -r --arg k "$key" '.data[$k]' <<<"$domains")|g")
done

for manifest in "${manifests[@]}"; do
  rendered=$(sed "${sed_args[@]}" "$manifest")
  if grep -q '\${[A-Z_]*}' <<<"$rendered"; then
    echo "$manifest: unresolved placeholders: $(grep -o '\${[A-Z_]*}' <<<"$rendered" | sort -u | tr '\n' ' ')" >&2
    exit 1
  fi
  if [ -n "${DRY_RUN:-}" ]; then
    echo "$rendered"
  else
    kubectl apply -f - <<<"$rendered"
  fi
done
