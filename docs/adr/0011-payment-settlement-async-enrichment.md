# ADR-0011: Payment Settlement — Async Enrichment and Provider-Agnostic Record

**Date:** 2026-06-10
**Status:** Accepted

When a Stripe `payment_intent.succeeded` webhook is received, the system records a Payment Settlement capturing gross amount, Stripe Processing Fee, Platform Fee, Net Amount, and payment method. Two non-obvious decisions govern how this is done.

## Decision 1: Async enrichment over synchronous Stripe API call

The webhook handler (`StripeWebhookController`) calls `paymentStatusUpdateGateway.settle()` immediately and then publishes a `StripePaymentSettled` domain event carrying the `paymentRequestId`, Stripe PaymentIntent ID, connected account ID, gross amount, currency, and application fee amount. A `StripeSettlementEnrichmentListener` in `payments.stripe` handles the event asynchronously: it fetches the Charge and its linked BalanceTransaction from the Stripe API using the connected account ID, then calls a `PaymentSettlementGateway` (named interface in the `order` module) to persist the Payment Settlement.

The Charge's `balance_transaction` field is an expandable reference. Enrichment MUST retrieve the BalanceTransaction explicitly by ID when Stripe returns only the reference, and MUST NOT persist a zero Processing Fee when the BalanceTransaction is unavailable. The Processing Fee excludes any `application_fee`; that amount is recorded separately as the Platform Fee.

### Why not synchronous

The BalanceTransaction is not in the `payment_intent.succeeded` webhook payload — fetching it requires a Stripe API call. Making that call inline in the webhook handler would:
- Add 200–500ms latency to the webhook response, risking Stripe's 30-second timeout on retried delivery
- Couple settlement recording to Stripe API availability (if the enrichment call fails, the webhook response is delayed or fails, triggering a Stripe retry that re-settles the payment)

The async path keeps the webhook response under 100ms and makes enrichment independently retriable without re-triggering settlement.

### Event payload

The `StripePaymentSettled` event carries enough data for the enrichment listener to work without querying the database:
- `paymentRequestId` — to identify the Payment Attempt to link the settlement to
- `stripePaymentIntentId` — to retrieve the Charge
- `connectedAccountId` — required for the Stripe API call against the connected account (null for platform-account charges)
- `grossAmountMinor`, `currency`, `applicationFeeAmountMinor` — available from the PaymentIntent in the webhook payload, avoiding a redundant Stripe fetch for data we already have

## Decision 2: Provider-agnostic PaymentSettlement entity in the order module

A single `payment_settlements` table (owned by the `order` module) stores settlement records for all providers. Stripe enrichment writes to it via `PaymentSettlementGateway`, a named interface exposed by the `order` module. The record is keyed by `payment_request_id` with a unique constraint.

### Schema

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `payment_request_id` | UUID unique | links to `order_payment_attempts` |
| `order_id_fk` | UUID | UUID FK for joins |
| `order_id` | VARCHAR(64) | denormalised business ID (ORD-…) |
| `provider` | VARCHAR(32) | `STRIPE`, `TRUELAYER`, `BRAINTREE` |
| `currency` | CHAR(3) | ISO 4217 |
| `gross_amount_minor` | BIGINT | what was charged |
| `processing_fee_minor` | BIGINT | provider's processing charge |
| `platform_fee_minor` | BIGINT | BlitzPay application fee |
| `net_amount_minor` | BIGINT | gross − processing_fee − platform_fee |
| `payment_method` | VARCHAR(32) | `CheckoutPaymentMethod` value |
| `payment_method_detail` | VARCHAR(64) nullable | e.g. `visa`, `mastercard` |
| `funds_available_at` | TIMESTAMP nullable | provider-estimated date funds become available |
| `provider_payment_reference` | VARCHAR(255) nullable | Stripe PaymentIntent ID |
| `provider_charge_reference` | VARCHAR(255) nullable | Stripe charge ID |
| `provider_balance_tx_reference` | VARCHAR(255) nullable | Stripe balance_transaction ID |
| `provider_dashboard_url` | VARCHAR(1000) nullable | direct provider-console reconciliation link |
| `settled_at` | TIMESTAMP | webhook event timestamp |
| `created_at` | TIMESTAMP | row insert time |

### Why not a Stripe-specific entity in payments.stripe

A Stripe-specific table would force any cross-provider reporting query to union across multiple tables. The `reporting` module (which exposes `PaymentSettlement` data via summary and list APIs) would need to depend on provider-specific modules — breaking the module boundary pattern. A provider-agnostic record in `order` lets the `reporting` module query through a single `PaymentSettlementGateway` regardless of which provider settled the payment.

The trade-off is that Stripe-specific reference columns (`provider_charge_reference`, `provider_balance_tx_reference`) are nullable generics rather than named Stripe fields. This is acceptable because they are reconciliation references, not business logic inputs.

## Reporting API

A new `reporting` module exposes two endpoints:
- `GET /reporting/settlements/summary` — aggregate totals (gross, fees, net) for a Merchant over a date range, optionally broken down by Payment Method
- `GET /reporting/settlements` — paginated list of individual Payment Settlement records for a Merchant

Merchants may query only their own data. Admins may query any Merchant and additionally see `platform_fee_minor`.

## Alternatives considered

- **Synchronous Stripe API call in webhook handler** — rejected; couples webhook latency to Stripe API response time and risks retry storms on enrichment failure.
- **Stripe-specific entity in payments.stripe** — rejected; forces the reporting module to depend on provider-specific modules and complicates cross-provider aggregation.
- **Enrich PaymentAttempt with fee columns** — rejected; PaymentAttempt is provider-agnostic and lives in the order module — adding Stripe-specific financial columns there mixes concerns and leaks provider details into the order lifecycle.
- **Listen for `charge.succeeded` instead of `payment_intent.succeeded`** — considered; rejected because `payment_intent.succeeded` is already the settlement trigger and adding a second event type would create a race condition between the two paths reaching the PaymentSettlement record.
