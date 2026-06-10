# ADR-0012: No UUIDs in UI — Always Use Human-Readable Codes or Names

**Date:** 2026-06-10
**Status:** Accepted

UUIDs are internal database keys. They must never appear in any user-facing surface — neither displayed as read-only values nor accepted as user input.

## Decision

Every place in the admin dashboard and mobile apps where an entity must be identified to the user must use the entity's human-readable **code** (e.g. `ORD-20260610-0042`, `MERCH-00123`) or **name** (e.g. merchant trading name, product title, employee full name). The UUID travels only between client and server as an opaque token inside API payloads, URL path segments, and local state — it must never be rendered into a UI element the user reads or types into.

### Rule table

| Context | Correct | Wrong |
|---|---|---|
| Display an order reference | `ORD-20260610-0042` | `3f4a1b2c-…` |
| Display a merchant | Trading name ("Acme Coffee") | UUID |
| Select a merchant in a dropdown | Name + branch | UUID |
| Search / filter field | Search by name or code | "Enter merchant UUID" |
| Error message identifying a record | Code or name | UUID |
| Shareable deep-link | Human-readable slug or code | UUID in URL (acceptable only as a hidden segment, never shown) |
| Copyable reference for support tickets | `ORD-…` or `PAY-…` code | UUID |

## Why

UUIDs are meaningless to humans. Displaying them:
- Creates cognitive load — users cannot recognise, remember, or compare UUIDs.
- Invites copy-paste errors in support flows (wrong UUID length, hyphens omitted, visually similar characters).
- Breaks accessibility — screen readers announce 36-character random strings unintelligibly.
- Signals unfinished product — it is a consistent marker of internal tooling accidentally exposed in a consumer or operator product.

Every domain entity in BlitzPay already carries a short, human-readable code or name:

| Entity | Code / Name field |
|---|---|
| Order | `order_id` (e.g. `ORD-…`) |
| Merchant | `trading_name` / `merchant_code` |
| Branch | `branch_name` |
| Payment attempt | `payment_request_id` short-form or `provider_charge_reference` |
| Product / Offering | `title` |
| Appointment | `appointment_code` or scheduled time + service name |
| Employee | `full_name` |
| Settlement | `order_id` (denormalised `ORD-…`) |

## What counts as "user-facing"

- Any rendered text, label, badge, table cell, or heading the user reads.
- Any input, search box, filter chip, or form field the user types into.
- Any toast, alert, or error message.
- Any exported CSV or PDF the user downloads.

API response bodies, browser developer tools, server logs, and internal admin scripts are **not** user-facing and may contain UUIDs freely.

## Enforcement

- Code review must reject any PR that introduces a UUID in a rendered UI element or a form `placeholder`/`label` that asks the user to enter a UUID.
- When an entity lacks a human-readable code today, the fix is to add one — not to display the UUID in the interim.
- If a support workflow genuinely requires copying an internal ID, expose the UUID behind a labelled "Copy internal ID" action (icon-button, collapsed by default) so it is accessible but not the primary display value.
