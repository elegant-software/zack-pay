.PHONY: build up down deploy-local infra-diff infra-deploy infra-bootstrap help

AWS_REGION ?= eu-central-1
IMAGE_TAG  ?= latest

# ── Local development ─────────────────────────────────────────────────────────

## build: compile jar + build Docker image
build:
	./gradlew bootJar -x test -q
	docker build -t blitzpay:$(IMAGE_TAG) .

## up: start Floci + floci-ui + Postgres via docker compose
up:
	docker compose up -d --build
	@echo ""
	@echo "  Floci:     http://localhost:4566"
	@echo "  Floci UI:  http://localhost:3000"
	@echo "  Postgres:  localhost:5433"

## down: stop and remove all local containers
down:
	docker compose down

## deploy-local: build image → push to Floci ECR → deploy CDK stacks
deploy-local: up
	bash infra/scripts/deploy-local.sh

## floci-init: re-seed S3 + Secrets Manager in running Floci
floci-init:
	bash infra/scripts/init-aws.sh

# ── CDK infra (real AWS) ──────────────────────────────────────────────────────

## infra-bootstrap: bootstrap CDK in the target AWS account (run once)
infra-bootstrap:
	cd infra && npx cdk bootstrap

## infra-diff: show what CDK would change in real AWS
infra-diff:
	cd infra && npx cdk diff

## infra-deploy: deploy all CDK stacks to real AWS
infra-deploy:
	cd infra && npx cdk deploy --all --require-approval never

# ── Help ──────────────────────────────────────────────────────────────────────

## help: list available targets
help:
	@grep -E '^## ' Makefile | sed 's/## /  /'
