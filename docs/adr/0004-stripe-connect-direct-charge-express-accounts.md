# ADR-0004: Stripe Connect — Direct Charges with Express Accounts

**Date:** 2026-05-22
**Status:** Accepted

## Context

BlitzPay is replacing its platform-level Stripe integration (one shared secret key for all merchants) with Stripe Connect, so that each Merchant receives payments directly into their own Stripe account. Two decisions with real alternatives had to be made: the **charge model** and the **connected account type**.

### Charge model options

1. **Direct charge** — `PaymentIntent` is created on the Merchant's Connected Account using the platform secret key with a `Stripe-Account: acct_XXXX` header. Money goes straight to the Merchant. BlitzPay collects a configurable `application_fee_amount` off the top.
2. **Destination charge** — `PaymentIntent` is created on the BlitzPay platform account with `transfer_data.destination = acct_XXXX`. Money hits the platform first, then Stripe routes it to the Merchant automatically.
3. **Separate charges + transfers** — Platform charges the customer, then manually triggers a `Transfer` to the Merchant later. Maximum flexibility; most implementation complexity.

### Connected account type options

1. **Express** — Stripe hosts the KYC and banking onboarding form. BlitzPay creates the account shell via `POST /v1/accounts` and returns a Connect Onboarding URL via `POST /v1/account_links`. Stripe delivers a `account.updated` webhook when the account is verified.
2. **Standard** — Merchant must already have or create a Stripe account and authorise BlitzPay via OAuth. BlitzPay receives a Connected Account ID after the OAuth code exchange.
3. **Custom** — BlitzPay owns the entire onboarding UX and compliance surface. Maximum control; requires a dedicated compliance and fraud team.

## Decisions

1. **Direct charge** is the charge model.
2. **Express accounts** are the connected account type.

## Rationale

**Direct charge over destination charge:**
- Dispute liability sits with the Merchant (the connected account), not with BlitzPay. This is the correct liability boundary for a facilitator platform.
- Money lands in the Merchant's account immediately without a platform-to-merchant transfer step.
- `application_fee_amount` handles BlitzPay's cut atomically on the same PaymentIntent — no separate payout logic.
- Destination charges put BlitzPay on the hook for disputes and require the platform to hold and route funds, which is a regulatory burden we do not want.

**Express over Standard:**
- Stripe handles KYC, identity verification, and banking details on BlitzPay's behalf. BlitzPay never touches the Merchant's sensitive financial data.
- The Connect Onboarding URL flow (account shell + account link) requires only two Stripe API calls to initiate. Standard requires an OAuth redirect flow and code exchange.
- Express accounts can be created and onboarding triggered server-side when the Merchant enables Stripe in the merchant app — no separate OAuth consent screen needed on our side.
- Custom accounts are ruled out: the compliance and fraud surface is disproportionate for our current scale.

**`charges_enabled: true` as the authoritative completion signal:**
- The `return_url` redirect fires when the Merchant submits the form, not when Stripe verifies it. Activating payment capability before verification would allow unverified merchants to accept payments.
- The `account.updated` webhook with `charges_enabled: true` is Stripe's authoritative confirmation that the account can process live charges.

## Consequences

- `StripePaymentService.createIntent()` must pass `Stripe-Account: acct_XXXX` on every PaymentIntent call and include a configurable `application_fee_amount`.
- `CreateIntentResponse` gains a `connectedAccountId` field. The mobile Stripe SDK must pass this as the `stripeAccount` parameter when confirming the payment. Backend and mobile changes ship together.
- A new `merchant_stripe_connect` table stores `connected_account_id`, `charges_enabled`, `details_submitted`, `created_at`, `updated_at` at the Merchant level.
- Enabling Stripe as a payment channel in the merchant app triggers Connect Onboarding immediately — there is no "STRIPE enabled but not Connect-onboarded" state.
- The existing `POST /webhooks/stripe` endpoint is extended to handle `account.updated` events in addition to `payment_intent.*` events.
- A `GET /merchants/{merchantId}/stripe-connect/refresh` endpoint must exist to regenerate an expired Connect Onboarding URL.
- Platform-level `StripeProperties.secretKey` and `publishableKey` become the platform account credentials used for all Connect API calls. A new `applicationFeeAmount` property (configurable, may be zero) is added.