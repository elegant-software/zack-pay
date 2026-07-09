# ADR-0006: AWS deployment architecture

**Date:** 2026-06-02
**Status:** Accepted (in progress — domain name and CI/CD approval gate still open)

## ECS Fargate over AWS Lambda

The backend is deployed on ECS Fargate, not Lambda. Lambda was ruled out for two reasons: `QrPaymentSseController` holds SSE connections open for up to 5 minutes, which exceeds Lambda's request/response model without significant rearchitecting; and the Spring Boot + JPA + Liquibase + Spring Modulith stack incurs 10–30 second cold starts that would make Lambda unusable for interactive mobile clients.

## ECS Fargate over EKS

ECS Fargate was chosen over EKS. EKS charges $72/month for the control plane before a single container runs. With one backend service and Keycloak, that cost is not amortised. EKS becomes the right call when 10+ services share a cluster across multiple teams.

## Public subnets for Fargate tasks (no NAT Gateway)

Fargate tasks run in public subnets with public IPs assigned. RDS remains in private subnets with no internet access. A NAT Gateway would cost ~$32/month and exists only to give private-subnet tasks outbound internet access (TrueLayer, Stripe, Expo Push). Security groups lock down all inbound traffic to the ALB only — the public IP is for outbound, not inbound exposure. Revisit when compliance requirements demand private subnets.

## Shared RDS instance for BlitzPay and Keycloak

BlitzPay and Keycloak share one RDS `db.t3.micro` instance with separate PostgreSQL databases (`blitzpay` and `keycloak`) and separate database users. A second RDS instance for Keycloak alone costs $13–26/month with no operational benefit at current scale. Upgrade to `db.t3.small` if connection pressure becomes visible.

## CDK TypeScript for infrastructure

Infrastructure is defined in AWS CDK using TypeScript, not Java or Terraform. CDK was chosen over Terraform because the deployment is AWS-only and CDK's L2 constructs for ECS, RDS, and VPC reduce boilerplate significantly. TypeScript was chosen over Java CDK because CDK was written in TypeScript — all documentation, examples, and community support are TypeScript-first; the Java bindings are auto-generated and noticeably more verbose.

## Spring Cloud AWS over raw AWS SDK

The application integrates with AWS services via **Spring Cloud AWS 4.0.2** rather than the raw `software.amazon.awssdk` client directly. Spring Cloud AWS provides auto-configuration for S3 (`spring-cloud-aws-starter-s3`) and Secrets Manager (`spring-cloud-aws-starter-secrets-manager`), and its `@ServiceConnection` support wires Floci endpoints automatically in tests. The raw `software.amazon.awssdk:s3` dependency is removed.

## AWS S3 over self-hosted MinIO

The storage backend migrates from a self-hosted MinIO instance (`minio.zackpay.de`) to AWS S3 in `eu-central-1`. MinIO was the interim solution before AWS deployment; S3 eliminates the MinIO operational overhead and is the natural fit for ECS Fargate. Locally, Floci emulates S3 via Docker Compose. The `blitzpay.storage.endpoint/accessKey/secretKey/pathStyleAccess/region` properties are removed; only `blitzpay.storage.bucket` remains.

## Secrets Manager for runtime secrets

Application secrets (Stripe keys, Braintree private key, Keycloak admin credentials, Expo access token) are loaded from AWS Secrets Manager at bootstrap via `spring.config.import`. Secrets are grouped by provider:

- `/zackpay/stripe` — `stripe.secret-key`, `stripe.webhook-secret`, `stripe.connect-webhook-secret`
- `/zackpay/braintree` — `braintree.private-key`
- `/zackpay/keycloak` — `keycloak.iam.admin-client-id`, `keycloak.iam.admin-client-secret`
- `/zackpay/expo` — `blitzpay.expo.access-token`

Non-sensitive config (public keys, URLs, realm names) stays in `application.yml` with env-var substitution.

The import is declared in `application.yml` (always active). Locally, Floci emulates Secrets Manager; an init container seeded from a `.env` file populates the secrets before the app starts. In the `contract-test` profile, `spring.cloud.aws.secretsmanager.enabled=false` disables the integration.

## Floci for local AWS emulation

**Floci** (`floci/floci:latest`) is used as the local AWS service emulator, replacing the previous MinIO S3 setup. It runs as a service in `docker-compose.yml`. For tests, `io.floci:testcontainers-floci` + `io.floci:spring-boot-testcontainers-floci` provide `FlociContainer` with `@ServiceConnection` for zero-config endpoint wiring. LocalStack was considered but its community edition is archived; Floci is a maintained, free alternative.

## AWS region: eu-central-1 (Frankfurt)

All AWS resources (ECS, RDS, S3, Secrets Manager) are deployed in `eu-central-1`. The platform operates under German domain names and targets EU data residency.

## CDK infrastructure in `infra/` within the application repo

The CDK TypeScript project lives at `infra/` in the `blitz-pay` root, alongside the existing `helm/` and `k8s/` directories. A separate infrastructure repository was considered but ruled out: CDK is TypeScript with its own `package.json`/`cdk.json`/`tsconfig.json`; Gradle and Spring Modulith are completely blind to it. Co-locating infra and app allows a single PR to change both (e.g. adding an env var and its Secrets Manager entry together). A dedicated infra repo would be a 6th repository for a single ECS service.

CDK stacks are split by concern: `NetworkStack` (VPC/subnets/security groups), `DataStack` (shared RDS), `SecretsStack` (Secrets Manager secrets), `EcrStack` (Docker image repository), `AppStack` (ECS Fargate, ALB, task definition, IAM role). The ECS task role is granted exactly `s3:GetObject/PutObject/DeleteObject` on the storage bucket and `secretsmanager:GetSecretValue` on `/zackpay/*`.

## Open decisions

- **Domain name** for the backend API (needed before any mobile app release — mobile clients embed the URL and cannot be force-updated)
- **CI/CD prod approval gate** — automatic deploy on push to main vs manual approval step before ECS service update
