#!/usr/bin/env bash
# Creates/updates every Kubernetes Secret oracle-dev needs (see ORACLE-DEV.md).
#   ./helm/oracle-dev-secrets.sh [namespace]          (default: bonami-dev)
#
# Safe to re-run: Enter keeps the value already in the cluster, so you can fill
# keys in later (e.g. the Keycloak admin client once the realm exists).
# Always targets the oracle-dev kubeconfig, never your current/staging context.
set -euo pipefail

NS="${1:-bonami-dev}"
export KUBECONFIG="${ORACLE_KUBECONFIG:-$HOME/.kube/oke-oracle-dev}"
export PATH="$HOME/.local/bin:$PATH"  # pipx-installed oci CLI, needed by the OKE kubeconfig

[ -t 0 ] || { echo "Interactive script: run it in a real terminal (not via Claude Code '!' or a pipe)."; exit 1; }
[ -f "$KUBECONFIG" ] || { echo "kubeconfig not found: $KUBECONFIG (see ORACLE-DEV.md)"; exit 1; }

echo "Writing secrets to $(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'), namespace $NS (Ctrl+C to abort)"

kubectl get namespace "$NS" >/dev/null 2>&1 || kubectl create namespace "$NS"

# current <secret> <key>: value already stored in the cluster, or empty
current() {
  kubectl get secret "$1" -n "$NS" -o "jsonpath={.data['$2']}" 2>/dev/null | base64 -d 2>/dev/null || true
}

# ask <var> <secret> <key> <label> [hidden|plain] [generate]
#   Enter keeps the cluster value; with "generate" and nothing stored, a random one is created.
ask() {
  local var=$1 secret=$2 key=$3 label=$4 mode=${5:-hidden} gen=${6:-} existing input hint
  existing=$(current "$secret" "$key")
  if [ -n "$existing" ]; then hint="empty = keep current"
  elif [ -n "$gen" ]; then hint="empty = generate random"
  else hint="empty = skip"; fi

  if [ "$mode" = "plain" ]; then
    read -r -p "  Enter $label [$hint]: " input
  else
    read -r -s -p "  Enter $label (hidden) [$hint]: " input; echo
  fi

  if [ -z "$input" ]; then
    if [ -n "$existing" ]; then input=$existing
    elif [ -n "$gen" ]; then input=$(openssl rand -hex 24); echo "    -> generated"
    fi
  fi
  printf -v "$var" '%s' "$input"
}

# apply_generic <secret> KEY=VAR ...: (re)writes the secret with the non-empty values
apply_generic() {
  local secret=$1; shift
  local args=() pair key var val
  for pair in "$@"; do
    key=${pair%%=*}; var=${pair#*=}; val=${!var}
    [ -n "$val" ] && args+=("--from-literal=$key=$val")
  done
  kubectl create secret generic "$secret" -n "$NS" ${args[@]+"${args[@]}"} \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "  ✓ $secret (${#args[@]} keys)"
}

# ---------------------------------------------------------------------------
echo; echo "1/5 blitzpay-platform-secrets (Postgres, Keycloak, Cloudflare Tunnel)"
PG_PASS=$(current blitzpay-platform-secrets POSTGRES_PASSWORD)
if [ -n "$PG_PASS" ]; then
  # Postgres keeps the password from its first start; changing only the secret would lock everyone out.
  echo "  POSTGRES_PASSWORD: keeping existing (fixed after first Postgres start)"
else
  ask PG_PASS blitzpay-platform-secrets POSTGRES_PASSWORD "Postgres password" hidden generate
fi
ask KC_PASS blitzpay-platform-secrets KC_BOOTSTRAP_ADMIN_PASSWORD "Keycloak admin password" hidden generate
ask CF_TOKEN blitzpay-platform-secrets CLOUDFLARE_TUNNEL_TOKEN "Cloudflare tunnel token (eyJ...)"
[ -n "$CF_TOKEN" ] || echo "  ! no tunnel token: cloudflared will not start until you re-run with it"
apply_generic blitzpay-platform-secrets \
  POSTGRES_PASSWORD=PG_PASS KC_BOOTSTRAP_ADMIN_PASSWORD=KC_PASS CLOUDFLARE_TUNNEL_TOKEN=CF_TOKEN

# ---------------------------------------------------------------------------
echo; echo "2/5 blitz-pay-secrets (application; DB values are filled in automatically)"
# shellcheck disable=SC2034  # read indirectly by apply_generic
DB_URL="jdbc:postgresql://postgres:5432/blitzpay" DB_USER="blitzpay"
S=blitz-pay-secrets
ask TL_CLIENT_ID      $S TRUELAYER_CLIENT_ID              "TrueLayer client ID" plain
ask TL_CLIENT_SECRET  $S TRUELAYER_CLIENT_SECRET          "TrueLayer client secret"
ask TL_KEY_ID         $S TRUELAYER_KEY_ID                 "TrueLayer key ID" plain
ask TL_MERCHANT       $S TRUELAYER_MERCHANT_ACCOUNT_ID    "TrueLayer merchant account ID" plain
ask TL_TOKEN          $S TRUELAYER_ACCESS_TOKEN           "TrueLayer access token"
ask STRIPE_KEY        $S STRIPE_SECRET_KEY                "Stripe secret key"
ask STRIPE_WH         $S STRIPE_WEBHOOK_SECRET            "Stripe webhook secret"
ask STRIPE_CONNECT_WH $S STRIPE_CONNECT_WEBHOOK_SECRET    "Stripe Connect webhook secret"
ask BT_MERCHANT       $S BRAINTREE_MERCHANT_ID            "Braintree merchant ID" plain
ask BT_PUBLIC         $S BRAINTREE_PUBLIC_KEY             "Braintree public key" plain
ask BT_PRIVATE        $S BRAINTREE_PRIVATE_KEY            "Braintree private key"
ask KC_CLIENT_ID      $S KEYCLOAK_IAM_ADMIN_CLIENT_ID     "Keycloak admin client ID (after realm setup)" plain
ask KC_CLIENT_SECRET  $S KEYCLOAK_IAM_ADMIN_CLIENT_SECRET "Keycloak admin client secret"
ask EXPO_TOKEN        $S EXPO_ACCESS_TOKEN                "Expo push access token"
ask OPENAI_KEY        $S OPENAI_API_KEY                   "OpenAI API key (voice)"
apply_generic $S \
  SPRING_DATASOURCE_URL=DB_URL SPRING_DATASOURCE_USERNAME=DB_USER SPRING_DATASOURCE_PASSWORD=PG_PASS \
  TRUELAYER_CLIENT_ID=TL_CLIENT_ID TRUELAYER_CLIENT_SECRET=TL_CLIENT_SECRET TRUELAYER_KEY_ID=TL_KEY_ID \
  TRUELAYER_MERCHANT_ACCOUNT_ID=TL_MERCHANT TRUELAYER_ACCESS_TOKEN=TL_TOKEN \
  STRIPE_SECRET_KEY=STRIPE_KEY STRIPE_WEBHOOK_SECRET=STRIPE_WH STRIPE_CONNECT_WEBHOOK_SECRET=STRIPE_CONNECT_WH \
  BRAINTREE_MERCHANT_ID=BT_MERCHANT BRAINTREE_PUBLIC_KEY=BT_PUBLIC BRAINTREE_PRIVATE_KEY=BT_PRIVATE \
  KEYCLOAK_IAM_ADMIN_CLIENT_ID=KC_CLIENT_ID KEYCLOAK_IAM_ADMIN_CLIENT_SECRET=KC_CLIENT_SECRET \
  EXPO_ACCESS_TOKEN=EXPO_TOKEN OPENAI_API_KEY=OPENAI_KEY

# ---------------------------------------------------------------------------
echo; echo "3/5 blitz-pay-truelayer-pem (mounted as a file; the pod cannot start without this secret)"
PEM_PATH=""
if kubectl get secret blitz-pay-truelayer-pem -n "$NS" >/dev/null 2>&1; then
  read -r -p "  Enter path to new TrueLayer PEM [empty = keep current]: " PEM_PATH
else
  read -r -p "  Enter path to TrueLayer EC private key PEM [empty = placeholder]: " PEM_PATH
  if [ -z "$PEM_PATH" ]; then
    kubectl create secret generic blitz-pay-truelayer-pem -n "$NS" --from-literal=private.pem= >/dev/null
    echo "  ✓ blitz-pay-truelayer-pem (empty placeholder; TrueLayer calls will fail until replaced)"
  fi
fi
if [ -n "$PEM_PATH" ]; then
  PEM_PATH=${PEM_PATH/#\~/$HOME}
  [ -f "$PEM_PATH" ] || { echo "  file not found: $PEM_PATH"; exit 1; }
  kubectl create secret generic blitz-pay-truelayer-pem -n "$NS" --from-file=private.pem="$PEM_PATH" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "  ✓ blitz-pay-truelayer-pem"
fi

# ---------------------------------------------------------------------------
echo; echo "4/5 minio-credentials (S3 storage; the pod cannot start without this secret)"
ask MINIO_USER minio-credentials root-user     "MinIO access key / user" plain
ask MINIO_PASS minio-credentials root-password "MinIO secret key / password"
apply_generic minio-credentials root-user=MINIO_USER root-password=MINIO_PASS

# ---------------------------------------------------------------------------
echo; echo "5/5 ghcr-pull-secret (pulls ghcr.io/elegant-software/blitz-pay)"
replace=y
if kubectl get secret ghcr-pull-secret -n "$NS" >/dev/null 2>&1; then
  read -r -p "  Exists. Replace? [y/N] " replace
fi
if [ "$replace" = "y" ] || [ "$replace" = "Y" ]; then
  read -r -p "  Enter GitHub username: " GH_USER
  read -r -s -p "  Enter GitHub token with read:packages (hidden): " GH_TOKEN; echo
  kubectl create secret docker-registry ghcr-pull-secret -n "$NS" \
    --docker-server=ghcr.io --docker-username="$GH_USER" --docker-password="$GH_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  echo "  ✓ ghcr-pull-secret"
fi

echo
echo "Done. Secrets in $NS:"
kubectl get secrets -n "$NS"
echo
echo "Keycloak login: https://oc-auth.zackpay.de/admin  user: admin  password:"
echo "  kubectl get secret blitzpay-platform-secrets -n $NS -o jsonpath='{.data.KC_BOOTSTRAP_ADMIN_PASSWORD}' | base64 -d; echo"
