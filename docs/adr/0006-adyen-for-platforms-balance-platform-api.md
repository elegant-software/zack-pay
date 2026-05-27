# ADR-0006: Adyen for Platforms — Balance Platform API

**Date:** 2026-05-27
**Status:** Accepted

## Context

BlitzPay supports Stripe Connect (ADR-0004) as its first platform PSP — each Merchant receives payments into their own Stripe Connected Account and BlitzPay takes an application fee. The requirement is to add Adyen as an alternative platform PSP with the same liability model: money flows to the Merchant's sub-account, BlitzPay takes a platform fee, and Adyen hosts the KYC/onboarding flow.

Adyen offers several distinct integration models, and Adyen itself has two generations of its platform API. Both choices had real alternatives.

### Adyen integration model options

1. **Adyen for Platforms (Balance Platform API)** — BlitzPay is the platform. Each Merchant gets an Account Holder (`legalEntityId`) and a Balance Account (`balanceAccountId`). Adyen hosts KYC. BlitzPay routes a split on every payment: the Merchant's share lands in their Balance Account; BlitzPay's platform fee lands in the BlitzPay platform Balance Account.
2. **Adyen standard (no platform model)** — BlitzPay processes all payments on its own Adyen merchant account and manually transfers funds to Merchants afterward. BlitzPay owns the full money flow and dispute liability.
3. **Merchant-brings-their-own-Adyen-account** — Merchant already has an Adyen account and authorises BlitzPay to act on their behalf. Analogous to Stripe Connect Standard (the model explicitly rejected in ADR-0004).

### Adyen platform API generation options

1. **Balance Platform API** (current, recommended since 2022) — `POST /legalEntities` → `legalEntityId`; `POST /accountHolders` → `accountHolderId`; `POST /balanceAccounts` → `balanceAccountId`; onboarding via `POST /legalEntities/{id}/onboardingLinks`; completion signal is `balanceAccount.updated` webhook with `receivePayments` capability = `allowed`.
2. **Classic MarketPay API** (legacy) — `accountHolderCode` (caller-assigned string), `Account` object, KYC via `ACCOUNT_HOLDER_VERIFICATION` notifications. Still operational but Adyen is actively migrating customers off it.

### PSP exclusivity options

1. **One active platform PSP per Merchant** — a Merchant operates on Stripe Connect or Adyen at any given time, not both. The active PSP is chosen in the admin dashboard. Enabling one means the other is not in use.
2. **Both PSPs active simultaneously** — a Merchant can have both a Stripe Connected Account and an Adyen Account Holder live at the same time. Checkout or payment method determines routing.
3. **Both active, merchant configures a preferred PSP** — both provisioned, one designated as primary for all card payments.

### Extension table shape options

For the `merchant_adyen_provider_accounts` extension table (parallel to `merchant_stripe_provider_accounts`):

1. **Store all three Adyen identifiers** — `legal_entity_id`, `account_holder_id`, `balance_account_id`.
2. **Store only `legal_entity_id` + `balance_account_id`** — the two needed at runtime (onboarding refresh and payment routing). `account_holder_id` dropped as currently unused.

### Onboarding trigger options

1. **Admin-initiated** — the admin dashboard creates the Account Holder shell on Adyen's API and returns an Adyen Onboarding URL; the admin forwards it to the Merchant to complete KYC.
2. **Merchant self-serves** — the Merchant triggers onboarding from their own merchant app portal (same UX shape as the current Stripe Connect flow in ADR-0004).

---

## Decisions

1. **Adyen for Platforms, Balance Platform API** is the integration model and API generation.
2. **One active platform PSP per Merchant** — mutually exclusive, chosen in the admin dashboard.
3. **Store all three Adyen identifiers** in `merchant_adyen_provider_accounts`.
4. **Admin-initiated onboarding** — `AdyenConnectController` is admin-facing, not merchant-facing.
5. **`payments.adyen`** is a new Spring Modulith module, parallel to `payments.stripe`.
6. **`AdyenProperties.applicationFeeAmount`** mirrors `StripeProperties.applicationFeeAmount` — same semantics, separate config block. BlitzPay's platform-level Adyen Balance Account ID is also held in `AdyenProperties`.

---

## Rationale

### Adyen for Platforms over standard or bring-your-own

- Dispute liability sits with the Merchant's Account Holder, not with BlitzPay. This is the same liability boundary chosen in ADR-0004 and must not regress.
- Money lands in the Merchant's Balance Account via split routing without a manual transfer step.
- Adyen hosts KYC; BlitzPay never touches the Merchant's sensitive financial data.
- Option 2 (standard, no platform model) puts BlitzPay on the hook for disputes and requires manual fund transfers — a regulatory and operational burden rejected in ADR-0004 for the same reasons.
- Option 3 (bring-your-own) is the Adyen equivalent of Stripe Connect Standard, which was explicitly rejected in ADR-0004 because it requires merchants to already have an account and introduces an OAuth consent flow.

### Balance Platform API over classic MarketPay

- Balance Platform is Adyen's strategic investment; classic MarketPay carries migration risk.
- Balance Platform maps cleanly onto the existing Stripe Connect shape: one API call to create the legal entity, one to create the account holder, one to create the balance account, one to generate an onboarding link. The completion signal (`balanceAccount.updated` + `receivePayments` = `allowed`) is a direct analog to Stripe's `account.updated` + `charges_enabled: true`.
- Classic MarketPay's `accountHolderCode` is caller-assigned and string-based, introducing a coordination burden that Adyen-assigned identifiers avoid.

### PSP exclusivity

- Running two competing connected-account setups simultaneously creates split dispute ownership, parallel reconciliation streams, and ambiguous routing at payment time.
- The `enabled` flag on `MerchantProviderAccount` already provides the enforcement mechanism: at most one platform PSP row may have `enabled = true` per Merchant.
- Options 2 and 3 (simultaneous or preferred-primary) are deferred — they require a payment-method-to-PSP routing layer that does not exist and is not in scope.

### All three Adyen identifiers stored

- `legal_entity_id` is required for every Adyen Onboarding URL refresh call.
- `balance_account_id` is required on every payment split.
- `account_holder_id` is required for Adyen account management operations (suspend, close, payout schedule, limits). Dropping it now means a schema migration the first time any of those operations are needed.
- The cost is one extra column with no runtime overhead.

### Admin-initiated onboarding

- The PSP choice is made by the admin, not the Merchant. It is consistent that account creation is also admin-triggered.
- The Adyen Onboarding URL can be shared or forwarded by the admin once generated, without requiring the Merchant to have a separate self-service portal flow.
- `AdyenConnectController` is in `payments.adyen.web` but secured for admin access only, distinct from the merchant-facing `StripeConnectController`.

### `payments.adyen` as a new module

- One PSP per Spring Modulith module is the established pattern (`payments.stripe`, `payments.truelayer`, `payments.braintree`).
- Hard module boundaries prevent Adyen-specific code from leaking into or being accidentally coupled with Stripe-specific code.
- Each module can be tested in isolation via `@ApplicationModuleTest`.

---

## Consequences

- A new `merchant_adyen_provider_accounts` table stores `legal_entity_id`, `account_holder_id`, `balance_account_id`, `receive_payments_enabled`, `details_submitted`, `created_at`, `updated_at`, with a 1:1 FK to `merchant_provider_accounts`.
- `AdyenConnectGateway` interface is added to `merchant.api` (parallel to `StripeConnectGateway`), implemented in `merchant.service`.
- `payments.adyen` module contains: `web/` (admin-facing `AdyenConnectController`), `service/` (`AdyenConnectService`), `outbound/` (`AdyenPaymentService`), `inbound/` (`AdyenWebhookController`), `config/` (`AdyenProperties`), `security/` (HMAC-SHA256 webhook signature verification).
- `AdyenProperties` holds `applicationFeeAmount` (platform fee) and `platformBalanceAccountId` (BlitzPay's own Balance Account ID for split routing).
- `MerchantCredentialResolverImpl` queries `merchant_provider_accounts` to determine which PSP is active; the check already exists and requires no structural change.
- `GET /merchants/{id}/adyen-connect/refresh` endpoint must exist to regenerate an expired Adyen Onboarding URL.
- ADR-0004 remains valid. ADR-0005 remains valid — `ADYEN` is already seeded in `payment_service_providers` and `MerchantProviderAccount` is already the single source of truth for PSP enablement.
- The `payments.adyen` module follows the package vocabulary in `reference/module-packaging-convention.md`; no `internal` sub-package is introduced.
