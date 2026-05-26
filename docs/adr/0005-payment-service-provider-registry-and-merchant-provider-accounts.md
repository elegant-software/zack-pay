# ADR-0005: Payment Service Provider Registry and Merchant Provider Accounts

**Date:** 2026-05-26
**Status:** Accepted

## Context

`MerchantPaymentChannel` was a hardcoded Kotlin enum (`PAYPAL`, `STRIPE`, `TRUELAYER`) stored as element collections on `MerchantApplication` and `MerchantBranch`. `MerchantStripeConnect` was a separate entity holding Stripe-specific credentials. Two problems arose:

1. **Duplication**: `activePaymentChannels` and `MerchantStripeConnect` were separate data stores that had to be kept in sync. A merchant could have `STRIPE` in their channel set with no Connect record, or vice versa.
2. **Non-extensibility**: Adding Adyen or Braintree required a new enum value and a new hardcoded entity. There was no consistent pattern for "merchant's account with PSP X".

## Decisions

### 1. Payment Service Provider reference table (`payment_service_providers`)

PSPs are stored as a seeded reference table, not a hardcoded enum. Initial seed: `STRIPE`, `TRUELAYER`, `ADYEN`, `BRAINTREE`, `PAYPAL`. New PSPs require a migration — runtime admin management is not supported, because a PSP row without a working integration is meaningless.

`PAYPAL` is retained as a seeded code because existing merchant data references it and Braintree (the gateway under the PayPal brand) is a separate PSP entry (`BRAINTREE`). The two were conflated in the old code; they are now distinct.

### 2. `MerchantProviderAccount` as the single source of truth

`MerchantProviderAccount` replaces both `activePaymentChannels` and `MerchantStripeConnect`. One row exists per (Merchant, PSP) pair. An `enabled = true` row is the authoritative record that a Merchant has that PSP active.

Rows are never deleted — disabling sets `enabled = false`. This preserves PSP-specific credentials (e.g. Stripe `connectedAccountId`) for re-activation without requiring a new onboarding flow.

`activePaymentChannels` is removed from `MerchantApplication` and `MerchantBranch` entirely.

### 3. PSP-specific extension tables over JSONB or nullable columns

Each PSP's specific fields live in a dedicated extension table (`merchant_stripe_provider_accounts`, future `merchant_adyen_provider_accounts`, etc.) joined 1:1 to `MerchantProviderAccount`. Alternatives rejected:

- **JSONB `metadata` column**: untyped, unindexable, breaks at schema boundaries.
- **Nullable columns per PSP on the base table**: the table grows a new nullable column per PSP — every row carries NULL columns for PSPs it doesn't use.

PSP-specific extension tables are created only when the PSP has real fields to store. `MerchantBraintreeProviderAccount` is deferred until Braintree sub-merchant IDs are implemented.

### 4. Capabilities are merchant-level only

`MerchantBranch.activePaymentChannels` is dropped without migration. Branches inherit whichever PSPs the parent Merchant has enabled. Branch-level PSP routing divergence would imply a different merchant, not a different branch.

### 5. Dedicated capability management endpoints

`activePaymentChannels` is removed from `UpdateMerchantRequest` and `MerchantDetailsResponse`. PSP enablement is managed via:

- `PUT /merchants/{id}/provider-accounts/{pspCode}` — enable
- `DELETE /merchants/{id}/provider-accounts/{pspCode}` — disable
- `GET /merchants/{id}/provider-accounts` — list with status

## Migration

1. Seed `payment_service_providers` table.
2. Create `merchant_provider_accounts` and `merchant_stripe_provider_accounts` tables.
3. Migrate `merchant_application_payment_channels` → `merchant_provider_accounts` (all rows get `enabled = true`; `PAYPAL` rows become `BRAINTREE`).
4. Migrate `merchant_stripe_connect` → `merchant_stripe_provider_accounts`, creating the corresponding `merchant_provider_accounts` row first.
5. Drop `merchant_application_payment_channels`, `merchant_branch_payment_channels`, `merchant_stripe_connect`.

## Consequences

- `MerchantCredentialResolverImpl.isChannelEnabled()` queries `merchant_provider_accounts` instead of `merchant.activePaymentChannels`.
- `MerchantDetailsResponse` no longer includes `activePaymentChannels`.
- ADR-0004 remains valid — Stripe Connect direct-charge Express account model is unchanged. Only the persistence layer is renamed.
