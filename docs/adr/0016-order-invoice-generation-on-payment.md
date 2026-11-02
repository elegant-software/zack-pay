# ADR-0016 — Order Invoice generation on payment

## Status
Accepted. The trigger was superseded on 2026-11-02 (issue #172). `OrderPaid` and `InvoiceOrchestrationListener` were removed because rendering on the payment path caused memory pressure. `InvoiceBatchJob` now renders invoices for paid orders and invoice orders on a schedule, using `OrderGateway.findInvoiceSource`, and emails them to the shopper. The buyer-contact and issue-date rules below still apply.

## Context
BlitzPay needs to generate a ZUGFeRD EN16931 compliant invoice for every paid Order and store it for later delivery via a mail service. The existing `invoice` module already provides PDF/XML generation. Two design questions required explicit decisions: when to trigger generation, and how to handle missing buyer address data.

## Decision

### Trigger: OrderStatus.PAID (moment 1), not PaymentSettlement (moment 2)

The `OrderStatusProjection` publishes an `OrderPaid` domain event immediately when `Order.status` transitions to `PAID`. The `invoice` module declares an `@ApplicationModuleListener` and reacts to it.

The alternative — triggering on `PaymentSettlement` creation — was rejected because:
- `PaymentSettlement` records are only produced by the Stripe enrichment path; TrueLayer and Braintree orders would silently never receive an invoice if those providers are re-enabled.
- `PaymentSettlement` carries the processing fee, platform fee, and net amount — internal merchant financial data that does not belong on a buyer-facing invoice.
- It adds async latency (balance transaction webhook) with no buyer-relevant gain.

### Buyer-Contact gating: skip silently when absent

`Buyer Contact` (name, street, zip, city, country) is an optional field on the order creation request. When absent, no invoice is generated and no error is raised. When present, a full EN16931 invoice is issued.

A fallback document (MINIMUM profile, quotation-style) was explicitly rejected to avoid maintaining two code paths and two document formats.

### VAT rate: Merchant Default VAT Rate

A nullable `defaultVatPercent` column is added to the merchant entity. The rate is applied uniformly to all line items on the invoice. When no rate is configured, 0% is used. Per-product VAT modelling is deferred — the domain complexity (per-category, per-country rules) is orthogonal to invoice generation and should be tackled separately.

### Storage: S3 + reference row

The generated PDF is stored in S3 under `invoices/{orderId}.pdf`. A lightweight `order_invoices` table records the S3 key, `orderId`, `generatedAt`, and `deliveryStatus` (starting as `PENDING`). The future mail service queries `PENDING` rows, fetches from S3, sends, and marks `SENT` — without re-generating the PDF.

### Module ownership: `invoice` module

The `invoice` module owns both generation and the listener. It declares Modulith dependencies on `order :: order-api`, `merchant :: MerchantInvoiceGateway`, and `storage`. A new `MerchantInvoiceGateway` named interface is added to the `merchant` module exposing name, address, registration number, default VAT rate, and logo storage key — separate from the existing `MerchantGateway` which is focused on onboarding lifecycle operations. The `invoice` module fetches the logo bytes from S3 and base64-encodes them before passing to `InvoiceData.logoBase64`; `InvoiceData` already supports this field.

## Consequences
- The order creation API gains an optional `buyerContact` request field (non-breaking).
- The merchant entity gains a nullable `defaultVatPercent` column (Liquibase migration, non-breaking).
- A new `MerchantInvoiceGateway` named interface is added to the merchant module.
- An `order_invoices` table is added (Liquibase migration).
- The `invoice` module gains a persistence layer and S3 dependency.
- VAT compliance is deferred: all invoices use 0% until a merchant configures their default rate.
