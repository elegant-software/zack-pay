# ADR-0010: Appointment Order Creation on Completion

**Date:** 2026-06-09
**Status:** Accepted

When a Merchant Manager marks an Appointment Completed and the Merchant has Deferred Payment enabled, a Pre-Order (Appointment Order) is created automatically from the Appointment's booked Product.

## Decision

### Trigger and offering guard

Order creation fires only when the Merchant has Deferred Payment enabled (which implicitly requires Pre-Order to be enabled). If the offering is absent, the Appointment is still marked Completed normally and no order is created — appointment completion is a calendar/service lifecycle event and must never fail due to payment configuration.

### Module communication: event-driven, one-way

`appointments` publishes an `AppointmentCompleted` domain event. The `order` module listens via `@ApplicationModuleListener` and creates the Appointment Order. No response event is published back to `appointments`.

A synchronous named interface call (like `OrderGateway`) was considered but rejected: it would introduce a compile-time dependency from `appointments` into `order`, inverting the natural direction of knowledge (the order module already knows about offerings and order creation rules; the appointments module should not need to know how to create an order).

### Reference direction: on Order, not on Appointment

The created Order stores `appointment_id` as a nullable foreign key. The `AppointmentEntity` has no `order_id` column. Apps query "orders where appointment_id = X" to find the Appointment Order for a given Appointment.

This avoids a second event round-trip (order → appointments to write back the order ID) and keeps the `appointments` schema stable. The trade-off is that the appointments module cannot directly surface the order ID — it relies on the calling app querying the order module.

### One Order per Appointment

A unique constraint on `orders.appointment_id` enforces that at most one Appointment Order can exist per Appointment. If the order is cancelled and a new one is needed, the merchant creates it manually through the normal order flow.

### Price and creator

The Appointment Order uses the **live Product price** at completion time, not the Appointment Product Snapshot price. The snapshot is informational; the order carries real money.

Creator type follows the Appointment Customer: `SHOPPER` for shopper-linked appointments, `MERCHANT` for contact-only appointments.

## Alternatives considered

- **Synchronous named interface** — rejected; creates a compile-time dependency in the wrong direction and couples appointment completion to order creation success.
- **Reference on Appointment (`order_id` column)** — rejected without a second round-trip event; keeping it event-driven requires the reference to live on the Order side.
- **Non-unique `appointment_id`** — considered; rejected in favour of a unique constraint to make the one-service-one-order invariant explicit at the DB level.
- **Always deferred payment, bypass offering check** — rejected; consistent offering enforcement avoids a hidden exception in the rules and prevents silent misconfiguration.
