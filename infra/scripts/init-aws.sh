#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="http://floci:4566"
AWS="aws --endpoint-url=$ENDPOINT"

upsert_secret() {
  local name="$1"
  local value="$2"
  if $AWS secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
    $AWS secretsmanager put-secret-value --secret-id "$name" --secret-string "$value"
  else
    $AWS secretsmanager create-secret --name "$name" --secret-string "$value"
  fi
}

echo "==> Creating S3 bucket"
$AWS s3api create-bucket \
  --bucket "${STORAGE_BUCKET:-blitzpay}" \
  --region eu-central-1 \
  --create-bucket-configuration LocationConstraint=eu-central-1 \
  2>/dev/null || echo "Bucket already exists, skipping"

echo "==> Seeding /blitzpay/stripe"
upsert_secret /blitzpay/stripe "{
  \"stripe.secret-key\": \"${STRIPE_SECRET_KEY:-}\",
  \"stripe.webhook-secret\": \"${STRIPE_WEBHOOK_SECRET:-}\",
  \"stripe.connect-webhook-secret\": \"${STRIPE_CONNECT_WEBHOOK_SECRET:-}\"
}"

echo "==> Seeding /blitzpay/braintree"
upsert_secret /blitzpay/braintree "{
  \"braintree.private-key\": \"${BRAINTREE_PRIVATE_KEY:-}\"
}"

echo "==> Seeding /blitzpay/keycloak"
upsert_secret /blitzpay/keycloak "{
  \"keycloak.iam.admin-client-id\": \"${KEYCLOAK_IAM_ADMIN_CLIENT_ID:-}\",
  \"keycloak.iam.admin-client-secret\": \"${KEYCLOAK_IAM_ADMIN_CLIENT_SECRET:-}\"
}"

echo "==> Seeding /blitzpay/expo"
upsert_secret /blitzpay/expo "{
  \"blitzpay.expo.access-token\": \"${EXPO_ACCESS_TOKEN:-}\"
}"

echo "==> AWS init complete"
