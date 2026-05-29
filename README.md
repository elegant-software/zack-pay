# BlitzPay

<!-- Build & Test -->
[![CI](https://github.com/medimohammadise/blitz-pay/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/medimohammadise/blitz-pay/actions/workflows/ci.yml)
[![CD](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd.yml/badge.svg?branch=main)](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd.yml)
[![Deploy](https://github.com/medimohammadise/blitz-pay/actions/workflows/deploy.yml/badge.svg?event=workflow_dispatch)](https://github.com/medimohammadise/blitz-pay/actions/workflows/deploy.yml)

<!-- PR Automation & Release -->
[![Auto-Label Pull Requests](https://github.com/medimohammadise/blitz-pay/actions/workflows/auto-label.yml/badge.svg?event=pull_request)](https://github.com/medimohammadise/blitz-pay/actions/workflows/auto-label.yml)
[![Release Notes](https://github.com/medimohammadise/blitz-pay/actions/workflows/release-notes.yml/badge.svg?event=pull_request)](https://github.com/medimohammadise/blitz-pay/actions/workflows/release-notes.yml)
[![Latest Release](https://img.shields.io/github/v/release/medimohammadise/blitz-pay)](https://github.com/medimohammadise/blitz-pay/releases)

<!-- Stack -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org)

## Documentation

- API versioning guide: [reference/api-versioning-guide.md](reference/api-versioning-guide.md)

## Contract Tests

`./gradlew contractTest` runs the API contract suite against a dedicated `contract-test` Spring profile.

To regenerate the checked-in OpenAPI document at `api-docs/api-doc.yml`, run:

```bash
./gradlew contractTest --tests com.elegant.software.blitzpay.contract.OpenApiGeneratorTest
```

This test fetches `/api-docs.yaml` from the running contract-test application context and writes the result to `api-docs/api-doc.yml`.

Important notes:
- The contract suite is intentionally isolated from production infrastructure.
- The `contract-test` profile excludes datasource, JPA, and Modulith event persistence auto-configuration.
- Outbound TrueLayer beans are mocked, so contract tests do not require credentials and do not call external APIs.
- On Spring Boot 4, these tests are implemented as handwritten `WebTestClient` tests under `src/contractTest/kotlin` rather than Spring Cloud Contract-generated verifier tests.

## Generating key pairs

For instructions on generating the key pairs used by this project (public/private keys and signing requests), see the official TrueLayer documentation:

https://docs.truelayer.com/docs/sign-your-payments-requests

Follow the steps on that page to create and manage your keys; then place your private key (and any config) into the project as appropriate (for example, the repo contains example key files at the project root).

## Environment variables

The application expects the following environment variables to be set before running. Use the placeholders below and replace them with your actual credentials and key path.

- TRUELAYER_CLIENT_ID: TrueLayer client identifier
  - Example: `<YOUR_CLIENT_ID>`
- TRUELAYER_CLIENT_SECRET: TrueLayer client secret (sensitive)
  - Example: `<YOUR_CLIENT_SECRET>`
- TRUELAYER_KEY_ID: Key identifier used when signing requests
  - Example: `<YOUR_KEY_ID>`
- TRUELAYER_MERCHANT_ACCOUNT_ID: Merchant account ID
  - Example: `<YOUR_MERCHANT_ACCOUNT_ID>`
- TRUELAYER_PRIVATE_KEY_PATH: Path (relative to project root or absolute) to the private key file used for signing (PEM)
  - Example: `path/to/your-private-key.pem`
Note: Real credential values have been intentionally redacted in this README. Never commit secrets into the repository.

Suggested minimal `.env` file (do NOT commit this to version control):

```env
TRUELAYER_CLIENT_ID=<YOUR_CLIENT_ID>
TRUELAYER_CLIENT_SECRET=<YOUR_CLIENT_SECRET>
TRUELAYER_KEY_ID=<YOUR_KEY_ID>
TRUELAYER_MERCHANT_ACCOUNT_ID=<YOUR_MERCHANT_ACCOUNT_ID>
TRUELAYER_PRIVATE_KEY_PATH=path/to/your-private-key.pem
```

Example zsh commands to export variables for your current shell session and run the application (replace placeholders with real values locally):

```zsh
export TRUELAYER_CLIENT_ID="<YOUR_CLIENT_ID>"
export TRUELAYER_CLIENT_SECRET="<YOUR_CLIENT_SECRET>"
export TRUELAYER_KEY_ID="<YOUR_KEY_ID>"
export TRUELAYER_MERCHANT_ACCOUNT_ID="<YOUR_MERCHANT_ACCOUNT_ID>"
export TRUELAYER_PRIVATE_KEY_PATH="path/to/your-private-key.pem"

./gradlew bootRun
```

Or run with variables inline (single command):

```zsh
TRUELAYER_CLIENT_ID="<YOUR_CLIENT_ID>" \
TRUELAYER_CLIENT_SECRET="<YOUR_CLIENT_SECRET>" \
TRUELAYER_KEY_ID="<YOUR_KEY_ID>" \
TRUELAYER_MERCHANT_ACCOUNT_ID="<YOUR_MERCHANT_ACCOUNT_ID>" \
TRUELAYER_PRIVATE_KEY_PATH="path/to/your-private-key.pem" \
./gradlew bootRun
```

Security and repo hygiene notes
- Never commit private keys or `.env` files containing secrets into version control. Add entries to `.gitignore` such as:

```
# Ignore local environment files and private keys
.env
*.pem
```

- Prefer storing secrets in a secure vault or CI secret manager for production deployments.

## Related Repositories

| Repository | Description |
|---|---|
| blitz-pay (backend) | Spring Boot / Kotlin backend — payments, merchants, orders |
| blitzpay-admin-dashboard | Angular admin dashboard |
| blitz-pay-merchant | React Native merchant app |
| blitz-pay (shopper app) | React Native shopper app |
