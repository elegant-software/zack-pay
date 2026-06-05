#!/usr/bin/env bash
# Local deploy: build image → push to Floci ECR → deploy CDK stacks via cdklocal.
# Assumes Floci is already running on localhost:4566.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra"
FLOCI_ENDPOINT="http://localhost:4566"
REGION="eu-central-1"
IMAGE_NAME="blitzpay"
IMAGE_TAG="${IMAGE_TAG:-latest}"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="$REGION"
export AWS_ENDPOINT_URL="$FLOCI_ENDPOINT"

log() { echo "==> $*"; }

# ── 1. Check Floci is reachable ───────────────────────────────────────────────
log "Checking Floci at $FLOCI_ENDPOINT"
if ! curl -sf "$FLOCI_ENDPOINT/_floci/health" >/dev/null 2>&1 && \
   ! curl -sf "$FLOCI_ENDPOINT" >/dev/null 2>&1; then
  echo "ERROR: Floci is not reachable at $FLOCI_ENDPOINT. Start it first."
  exit 1
fi

# ── 2. Build jar + Docker image ───────────────────────────────────────────────
log "Building jar"
cd "$REPO_ROOT"
./gradlew bootJar -x test --no-daemon -q

log "Building Docker image $IMAGE_NAME:$IMAGE_TAG"
docker build -f Dockerfile.local -t "$IMAGE_NAME:$IMAGE_TAG" .

# ── 3. Seed S3 + Secrets Manager ─────────────────────────────────────────────
log "Seeding Floci resources (S3 bucket + secrets)"
bash "$INFRA_DIR/scripts/init-aws.sh"

# ── 4. Push image to Floci ECR ────────────────────────────────────────────────
log "Pushing image to Floci ECR"
aws ecr describe-repositories --repository-names "$IMAGE_NAME" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "$IMAGE_NAME" >/dev/null

ECR_URI="localhost:4566/$IMAGE_NAME:$IMAGE_TAG"
docker tag "$IMAGE_NAME:$IMAGE_TAG" "$ECR_URI"

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "localhost:4566"

docker push "$ECR_URI"

# ── 5. CDK bootstrap (idempotent) + deploy ────────────────────────────────────
log "Bootstrapping CDK against Floci"
cd "$INFRA_DIR"
npm install --silent
npx cdklocal bootstrap --quiet 2>/dev/null || true

log "Deploying all CDK stacks"
npx cdklocal deploy --all --require-approval never --outputs-file cdk-outputs-local.json

log "Done. Outputs written to infra/cdk-outputs-local.json"
if command -v jq >/dev/null 2>&1; then
  jq '.BlitzPayApp // {}' "$INFRA_DIR/cdk-outputs-local.json"
fi
