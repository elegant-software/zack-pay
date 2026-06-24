#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Usage: $0 [path/to/.env]"
  exit 1
fi

get() { grep "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

REGION="eu-central-1"

aws secretsmanager put-secret-value --region "$REGION" \
  --secret-id /zackpay/stripe \
  --secret-string "$(jq -n \
    --arg sk "$(get STRIPE_SECRET_KEY)" \
    --arg wh "$(get STRIPE_WEBHOOK_SECRET)" \
    --arg cwh "$(get STRIPE_CONNECT_WEBHOOK_SECRET)" \
    '{"stripe.secret-key":$sk,"stripe.webhook-secret":$wh,"stripe.connect-webhook-secret":$cwh}')"

aws secretsmanager put-secret-value --region "$REGION" \
  --secret-id /zackpay/braintree \
  --secret-string "$(jq -n \
    --arg pk "$(get BRAINTREE_PRIVATE_KEY)" \
    '{"braintree.private-key":$pk}')"

aws secretsmanager put-secret-value --region "$REGION" \
  --secret-id /zackpay/keycloak \
  --secret-string "$(jq -n \
    --arg cid "$(get KEYCLOAK_IAM_ADMIN_CLIENT_ID)" \
    --arg cs "$(get KEYCLOAK_IAM_ADMIN_CLIENT_SECRET)" \
    '{"keycloak.iam.admin-client-id":$cid,"keycloak.iam.admin-client-secret":$cs}')"

aws secretsmanager put-secret-value --region "$REGION" \
  --secret-id /zackpay/expo \
  --secret-string "$(jq -n \
    --arg tok "$(get EXPO_ACCESS_TOKEN)" \
    '{"zackpay.expo.access-token":$tok}')"

echo "All secrets synced."
