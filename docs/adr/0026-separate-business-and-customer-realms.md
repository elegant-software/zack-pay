# ADR-0026: Separate Keycloak realms for business users and customers

**Date:** 2026-11-14
**Status:** Accepted. Supersedes the "one realm" part of ADR-0015.

Shoppers sign in to the realm `blitzpay-customers`. Merchant users, platform admins and Kitchen and Kiosk Logins sign in to `blitzpay-business`. Both realms are on the same Keycloak server.

Keycloak's "Email as username" is a realm-wide setting. Shoppers must use their email as username, but Kitchen and Kiosk Logins should keep plain usernames like `kitchen-br-1a2b3c4d5e6f` instead of `kitchen-br-…@kitchen.invalid`. Two realms let each side have its own login rules. A shopper token also can no longer carry business groups or roles, even through a realm misconfiguration.

## Decision

- **`blitzpay-customers`**: email as username, login with email, no duplicate emails. Group `/blitzpay-mobile-users`, client `blitzpay-mobile` (shopper app).
- **`blitzpay-business`**: plain usernames, login with email allowed. Groups `/merchants/…` and `/merchant-app-users/…`, realm role `blitzpay-admin`. Clients: admin dashboard, merchant app, Kitchen, Kiosk.
- **Backend** (`SecurityConfig.jwtDecoder`): accepts JWTs from both issuers. It picks the realm's decoder by the `iss` claim, and that decoder verifies the signature and issuer. Authorization stays in the service layer, as in ADR-0015. Business endpoints already check business groups and roles, which customer tokens don't have.
- **Admin client**: the service account `blitzpay-api` exists in both realms with the same secret, so the backend keeps one `KEYCLOAK_IAM_ADMIN_CLIENT_*` pair. The customer realm is used for shopper signup and for a shopper's own account export and deletion (`/v1/me`, chosen by the token's issuer).
- A person who is both shopper and merchant has two accounts, one per realm, with different Keycloak user IDs.

## Consequences

- Config: `KEYCLOAK_IAM_REALM` (default `blitzpay-business`) and `KEYCLOAK_IAM_CUSTOMER_REALM` (default `blitzpay-customers`).
- Each realm has its own Google identity provider redirect URI (`/realms/<realm>/broker/google/endpoint`), which must be registered with Google.
- oracle-dev still uses the single `blitzpay` realm for both (`application-oracle-dev.yml`).
- The staging migration on 2026-11-14 copied users from the old `blitzpay` realm with new IDs and remapped the IDs stored in the database. The old realm was left untouched as a fallback.
