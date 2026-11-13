# Guest invoice email — privacy note

Storefront guests (`CreatorType.GUEST`, `POST /v1/storefront/orders`) have no account. They may
optionally give an email address to receive the invoice of that one order.

## Legal basis and purpose

- **Basis:** explicit consent, DSGVO Art. 6(1)(a). The request must carry `invoiceEmailConsent: true`
  with `buyerEmail`; an email without consent is rejected with 400 `INVOICE_EMAIL_CONSENT_REQUIRED`
  and nothing is stored. The storefront checkbox is unticked by default and the fields are sent only
  when it is ticked.
- **Purpose:** sending the invoice PDF of that order, once. No marketing, no newsletter, no linking
  to other orders, no profiling. Any other use needs a new, separate consent.

## What is stored (`orders` table)

| Column | Kept |
|---|---|
| `invoice_email` | until the invoice email is sent, then set to `NULL` |
| `invoice_email_consent_at` | with the order (proof of consent, Art. 7(1)) |
| `invoice_email_consent_version` | with the order; identifies the consent wording (`OrderService.INVOICE_EMAIL_CONSENT_VERSION`, bump it when the storefront text changes) |

Nothing else about the guest is collected (no name, no address).

## Download without email

A paid guest order can download its invoice using `GET /v1/storefront/orders/{orderId}/invoice`
with its existing order-scoped guest cookie. This is independent of the email opt-in and SMTP.
The first authorized request creates the stored PDF. It returns an expiring 30-minute storage link
that can be shown as a QR code and used from another device without exposing the order API.
The link grants access only to that PDF; treat it as a private document link. An expired link is
renewed by the original authenticated app or guest browser. Invoice endpoints use `Cache-Control: no-store`.

## Deletion

`InvoiceBatchJob` calls `OrderGateway.clearGuestInvoiceEmail` right after the invoice is marked
`SENT`: the purpose is fulfilled, so the address is deleted (storage limitation, Art. 5(1)(e)).
We chose immediate deletion over a retention period because nothing else ever needs the address;
the trade-off is that a lost invoice can't be re-sent automatically — the guest asks the merchant.
While sending fails the address stays so the next run can retry.

Known gap: if an invoice is never generated (e.g. merchant profile missing), the address stays on
the order. Withdrawal of consent (Art. 7(3)) is handled on request by nulling `invoice_email`.

## Invoice without buyer address (§33 UStDV)

Guest invoices use the "—" buyer placeholder. For totals up to €250 gross this is a valid
*Kleinbetragsrechnung* (§33 UStDV): it needs the seller's name and address, issue date, items,
gross amount and tax rate — not the buyer. Above €250 a full §14(4) UStG invoice would need the
buyer's name and address; guests are consumers (B2C), for whom an invoice is not mandatory, so the
PDF serves as a receipt. A business guest that needs input-tax deduction must order with an account
and buyer contact.
