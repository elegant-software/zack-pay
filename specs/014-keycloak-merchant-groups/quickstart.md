# Quickstart: Keycloak Merchant & Branch Group Sync

## Prerequisites

- Keycloak running (locally via Docker or the cluster at `keycloak.zackpay.de`)
- A realm created (e.g., `blitzpay`)
- An admin service account (Client ID + Secret) with `realm-management` roles

## Environment Variables

Add to your local `.env` or export before running:

```bash
export KEYCLOAK_IAM_SERVER_URL=https://keycloak.zackpay.de
export KEYCLOAK_IAM_REALM=blitzpay
export KEYCLOAK_IAM_ADMIN_CLIENT_ID=blitzpay-admin
export KEYCLOAK_IAM_ADMIN_CLIENT_SECRET=<secret>
```

All other existing env vars remain unchanged.

## Run the Application

```bash
./gradlew bootRun
```

## Verify a Merchant Group is Created

1. Register a merchant via the API:

```bash
curl -X POST http://localhost:8080/v1/merchants/register \
  -H "Content-Type: application/json" \
  -d '{
    "businessProfile": {
      "legalBusinessName": "ABC Store",
      "businessType": "RETAIL",
      "registrationNumber": "DE123456789",
      "operatingCountry": "DE",
      "primaryBusinessAddress": "123 Main St, Berlin"
    },
    "primaryContact": {
      "fullName": "John Doe",
      "email": "john@abc.de",
      "phoneNumber": "+4917612345678"
    }
  }'
```

2. Check Keycloak Admin Console → Realm: `blitzpay` → Groups → `/merchants/merchant_<returned-uuid>` exists with attributes.

## Verify a Branch Group is Created

```bash
MERCHANT_ID=<uuid-from-step-above>

curl -X POST http://localhost:8080/v1/merchants/$MERCHANT_ID/branches \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Berlin Central Branch",
    "city": "Berlin",
    "country": "DE"
  }'
```

Check Keycloak: `/merchants/merchant_<merchantId>/branch_<returned-branchId>` exists.

## Run Tests

```bash
./gradlew check        # unit + contract tests
./gradlew test --tests "*.merchant.iam.*"   # IAM module tests only
```

## Local Keycloak (Docker)

```bash
docker run -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:26 start-dev
```

Then create realm `blitzpay` and a client `blitzpay-admin` with `Service Accounts Enabled` and the `realm-management` client roles assigned.
