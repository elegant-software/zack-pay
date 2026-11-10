# BlitzPay

<!-- Build & Test -->
[![CI](https://github.com/medimohammadise/blitz-pay/actions/workflows/ci-build.yml/badge.svg?branch=main)](https://github.com/medimohammadise/blitz-pay/actions/workflows/ci-build.yml)
[![CD](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd-build-image.yml/badge.svg?branch=main)](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd-build-image.yml)
[![Deploy](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd-deploy-k8s.yml/badge.svg?event=workflow_dispatch)](https://github.com/medimohammadise/blitz-pay/actions/workflows/cd-deploy-k8s.yml)

<!-- PR Automation & Release -->
[![Auto-Label Pull Requests](https://github.com/medimohammadise/blitz-pay/actions/workflows/pr-auto-label.yml/badge.svg?event=pull_request)](https://github.com/medimohammadise/blitz-pay/actions/workflows/pr-auto-label.yml)
[![Release Notes](https://github.com/medimohammadise/blitz-pay/actions/workflows/release-notes.yml/badge.svg?event=pull_request)](https://github.com/medimohammadise/blitz-pay/actions/workflows/release-notes.yml)
[![Latest Release](https://img.shields.io/github/v/release/medimohammadise/blitz-pay)](https://github.com/medimohammadise/blitz-pay/releases)

<!-- Stack -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org)

## Documentation

- API versioning guide: [reference/api-versioning-guide.md](reference/api-versioning-guide.md)

## Mobile catalog browsing

Authenticated mobile clients can use these additive endpoints, scoped by merchant and mandatory `branchId`:

- `GET /v1/merchants/{merchantId}/catalog/products?branchId=...&limit=24` returns `items`, `nextCursor`, `hasMore`, and `limit`. Limits are 1–100. Pass `nextCursor` unchanged as `cursor` for the next batch; a terminal page has a null cursor. Ordering is stable by original creation time, then product ID, including when names are translated. It does not reorder by translated product name.
- Optional `categoryId` filters a category; `uncategorized=true` selects products without a category. These filters are mutually exclusive. Changing merchant, branch, category, or visibility requires starting without a cursor.
- `GET /v1/merchants/{merchantId}/catalog/categories?branchId=...` returns nonempty `categories` with `productCount`, plus `uncategorizedCount` and `totalCount`. Clients provide their own translated “All” and “Uncategorized” labels.
- Both reads default to active products in an active branch. `includeInactive=true` requires authorized branch staff or a platform admin and also permits an inactive branch for management. Language resolution follows the existing `Accept-Language` / `language` behavior.

Product responses retain `imageUrl` for the larger image shown when an item opens. Optional `thumbnailUrl` is for list rows. JPEG/PNG uploads generate thumbnails with a maximum edge of 256 pixels, retaining aspect ratio and PNG transparency. Signed URLs have the existing storage TTL and should be refreshed when expired. Existing images and WebP (which the current ImageIO stack cannot decode) may have no thumbnail; clients must fall back to `imageUrl`. Browsing never generates thumbnails or downloads full images on the server.

Authorized branch staff/admins can backfill existing JPEG/PNG images with `POST /v1/merchants/{merchantId}/catalog/thumbnails?branchId=...&limit=10`. Limits are 1–24. Follow `nextCursor` until `hasMore=false`; `updated`, `skipped`, and `failed` describe each batch. Existing variants and unsupported formats are skipped. A failed batch/item can be retried; conditional updates avoid attaching a variant after a concurrent product edit. The original/detail image is never overwritten by backfill.

The existing `GET /products` array response is unchanged, including callers that need the complete branch catalog for basket validation. Apply Liquibase migration `20261110-001-mobile-catalog.sql` before running the updated backend. Mobile apps must explicitly adopt the new paginated endpoints and thumbnail field after deployment.

Catalog repository tests require Docker: they run PostgreSQL 16, apply the new migration, and exercise real queries and row-level tenant isolation without changing a shared database.

## Contract Tests

HTTP JSON uses the Jackson 3 Kotlin module so omitted request properties honor Kotlin constructor defaults
(for example, merchant order `usesDeferredPayment` defaults to `false`). Explicit `null` remains invalid
for non-nullable booleans. The Jackson 2 Kotlin module is retained for adapters using `com.fasterxml` APIs.

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


### Table QR storefront links (mobile #62 / backend #222)

Table QRs keep the same storefront URL contract: `<STOREFRONT_URL>/t/<tableCode>`.
The local default is storefront port 4201, not admin port 4200. Set
`STOREFRONT_URL` explicitly when a phone must reach another origin, then restart
the backend and reload the Table QR in the admin dashboard. Table links and
guest-payment returns use the same setting; existing table codes are unchanged.

Default-camera app opening is handled by verified HTTPS App/Universal Links,
not a backend redirect or a browser installation check. The storefront website
must publish matching Android/iOS association files; the shopper native build
must claim the same origin and preserve /t/<code> through login. The storefront
implementation/configuration lives in elegant-software/blitzpay-admin-dashboard,
subtask #91. Keep local API/data routing aligned with the selected storefront;
a public staging storefront does not automatically use a laptop API on 8080.
HTTP localhost/LAN links can exercise in-app scanning but do not provide verified
automatic camera-to-app opening. No deployment is implied by these code changes.
