# ADR-0017 — Invoice Order reuses the Order entity

## Status
Accepted

## Context
Merchants need to send invoices to shoppers for later payment — the shopper receives the invoice in their app and pays using the same payment methods available on the order screen (card, bank, pay-by-bank, tap-to-pay, paypal). The shopper app previously had hardcoded invoice screens (`InvoicesScreen`, `SendInvoiceScreen`) disconnected from the backend.

Two approaches were considered:

1. **Separate Invoice entity** — a new `invoices` table, new module, new payment pipeline, new shopper-facing screens.
2. **Reuse Order** — an Invoice Order is an Order with `usesDeferredPayment = true` and a `targetShopperId` pointing to the paying shopper.

## Decision

### Reuse Order with two new columns

An Invoice Order is modelled as an Order. Two nullable columns are added:

- `target_shopper_id` (varchar 512) — the Keycloak subject ID of the shopper who should see and pay the order. For non-invoice orders this is null; the shopper self-associates through QR or direct ordering.
- `due_date` (date) — optional merchant-set date indicating when payment is expected. Display-only; no enforcement or late penalties.

`created_by_id` remains the merchant user who created the order (audit trail). The shopper orders list query becomes `WHERE created_by_id = :id OR target_shopper_id = :id`.

### Recipient selection: past customers + email search

The merchant selects a Target Shopper by:
1. **Past customers** — shoppers who have previously ordered at the branch, derived from order history.
2. **Email search** — lookup via a new nullable `email` column on `shopper_profiles`, pre-populated from the Keycloak token's `email` claim.

### Shopper discovery: push notification + orders list

When an Invoice Order is created, the backend fires a push notification to the Target Shopper's registered devices. The order also appears in the shopper's normal orders list with invoice-style presentation (due date badge, merchant name).

### No separate Invoices screen

The shopper app's orders list includes Invoice Orders with distinct visual treatment. The existing `OrderDetailScreen` handles payment method selection and resume-payment flow. A separate Invoices screen would duplicate this UI for the same underlying data.

### Shopper app cleanup

The following hardcoded/disconnected screens are removed:
- `InvoicesScreen` (hardcoded fake invoices)
- `SendInvoiceScreen` (compose invoice to contacts — shopper-to-shopper, out of scope)
- `InvoicePdfPreviewScreen`
- `invoiceService.ts` (PDF generation service for the compose flow)
- `types/invoice.ts` (form state types for the compose flow)

`shopperInvoice.ts` (fetches real Order Invoice download URLs for paid orders) is retained.

## Consequences
- The `order_orders` table gains two nullable columns (`target_shopper_id`, `due_date`) via Liquibase migration. Non-breaking.
- The `shopper_profiles` table gains a nullable `email` column via Liquibase migration. Non-breaking.
- The shopper orders list query adds an `OR target_shopper_id = :id` condition. Index on `target_shopper_id` recommended.
- The merchant order creation endpoint accepts optional `targetShopperId` and `dueDate` fields when `usesDeferredPayment = true`.
- New merchant-facing endpoints: list Branch Customers, search shopper by email.
- Push notification infrastructure (already built for order status updates) is reused for Invoice Order creation notifications.
- Order Invoice (ZUGFeRD PDF) generation fires automatically when the Invoice Order is paid, via the existing `OrderPaid` event listener — no changes needed.
- Payment Settlement, Processing Fee, Platform Fee, and Net Amount all work unchanged because the payment flows through the merchant's Connected Account.
- Shopper-to-shopper invoicing is explicitly deferred — shoppers lack Connected Accounts for receiving payments.
