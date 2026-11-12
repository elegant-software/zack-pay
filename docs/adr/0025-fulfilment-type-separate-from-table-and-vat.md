# ADR-0025 — Fulfilment Type is its own order field; it does not drive VAT

## Status
Accepted

## Context

Shoppers and staff need to say whether an order is eaten in or taken away (zack-pay#256), and the Kitchen
Screen must show it. An order already has an optional Table, but a Table only says where the order is
brought. A counter-service Branch has no Tables yet has people eating in, and a seated shopper may want a
to-go box.

Until 2025 Germany taxed restaurant food at 19% dine-in and 7% take-away, which would have argued for a
VAT rate per Fulfilment Type. Since 1 January 2026 restaurant food is 7% in both cases and drinks stay at 19%
([Grant Thornton](https://www.grantthornton.de/en/insights/2025/catering-from-2026-7-on-food-19-on-beverages/)),
so the split is food vs drinks, not eat-in vs take-away.

## Decision

- `fulfilmentType` (`DINE_IN` | `TAKE_AWAY`) is a separate, required field on Walk-In and Pre-Orders. It is
  not derived from the Table. Appointment and Invoice Orders have none.
- It is fixed when the order is placed. There is no per-Branch setting to disable an option.
- It does **not** change VAT. The merchant-level Order VAT Rate stays the only rate.
- On requests it is optional: a Table gives `DINE_IN`, no Table gives `TAKE_AWAY`. The column is nullable only
  for Invoice and Appointment Orders; existing Walk-In and Pre-Orders are backfilled by the same rule.
- Staff can set it when creating an order in the merchant app, with the same default.
- The Kitchen Screen shows it. `OrderReadyForPickup` carries it and whether the order has a Table, so the
  push says "on its way to your table" (`DINE_IN` with a Table) or "collect at the counter" (everything else).

## Consequences

- Mixed carts (food at 7%, drinks at 19%) stay a known limitation, because per-product VAT is out of scope.
- A take-away-only Branch can still receive a dine-in order. Staff see the type and handle it.
- Old shopper app versions keep working through the derived default; the API can require the field later.
- If BlitzPay launches where take-away and dine-in are taxed differently, add a take-away rate that falls
  back to the Order VAT Rate. Nothing here blocks that.

## Considered Options

- **Derive it from the Table:** wrong for counter-service Branches and for seated shoppers who want take-away.
- **A take-away VAT rate now:** solves a problem Germany no longer has, and adds a column and admin UI.
- **Per-product VAT per Fulfilment Type:** the most accurate, but the catalog and MCP tooling would change.
- **Let the shopper or staff change it after placement:** needs a new endpoint, a live kitchen event and a lock rule
  for a rare case.
- **Rename `OrderReadyForPickup` to `OrderReady`:** cleaner, but breaks every listener.
