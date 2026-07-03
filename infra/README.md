# AWS bootstrap

CDK app for the AWS deployment described in [ADR-0006](../docs/adr/0006-aws-deployment-architecture.md). Region: `eu-central-1` (+ `us-east-1` only for the CloudFront cert).

## Prerequisites

- AWS CLI configured with credentials for the target account (`aws sts get-caller-identity` should work)
- Node 20+, `npm install` run in this directory

## First-time bootstrap (new AWS account)

```bash
cd infra
npm install

# CDK needs a bootstrap stack in every region it deploys to.
# us-east-1 is required because WebAdminCertStack must live there (ACM certs for CloudFront).
npx cdk bootstrap aws://ACCOUNT_ID/eu-central-1
npx cdk bootstrap aws://ACCOUNT_ID/us-east-1

# Deploy everything. CDK resolves stack dependencies (Network -> Data/Secrets/Ecr -> App -> Dashboard,
# WebAdminCert -> WebAdmin -> CiCd) automatically via app.ts's prop wiring.
npx cdk deploy --all
```

## After first deploy

1. **Secrets** — `SecretsStack` creates `/zackpay/{stripe,braintree,keycloak,keycloak-admin,expo}` with placeholder `REPLACE_ME` values. Populate real values:
   ```bash
   ./scripts/sync-secrets.sh path/to/.env
   ```
2. **GitHub Actions** — `CiCdStack` provisions the OIDC deploy role. Wire it up plus the rest of the CI env with:
   ```bash
   ../.github/scripts/setup-github-env.sh
   ```
3. Redeploy the app once secrets are real so ECS picks them up: `npx cdk deploy ZackPayApp`.

## Redeploying a single stack

```bash
npx cdk deploy ZackPayApp        # e.g. after an AppStack change
npx cdk diff ZackPayApp          # preview first
```

Stack names: `ZackPayNetwork`, `ZackPayData`, `ZackPaySecrets`, `ZackPayEcr`, `ZackPayBastion`, `ZackPayKeycloak`, `ZackPayApp`, `ZackPayDashboard`, `ZackPayWebAdminCert`, `ZackPayWebAdmin`, `ZackPayCiCd`.

## Local dev (not real AWS)

`scripts/init-aws.sh` seeds the Floci (local AWS emulator) container used by `docker-compose.yml` — it targets `floci:4566`, never a real account. Not part of the AWS bootstrap flow above.
