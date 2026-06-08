# ADR-0009: AppointmentQueryService as a Named Interface for Cross-Module Guard Queries

**Date:** 2026-06-08
**Status:** Accepted

## Context

The `merchant` module owns Employee, Branch, Product, and Product Category lifecycle. Several mutation operations in that module must be guarded by checking whether future Booked appointments exist before allowing the change:

- Deactivating an Employee — blocked while the employee has future Booked appointments
- Removing an Employee Branch Assignment — blocked while the employee has future Booked appointments at that branch
- Removing an Employee Service Assignment — blocked while the employee has future Booked appointments for products in that category
- Deactivating a Branch — blocked while the branch has future Booked appointments
- Deactivating a Merchant — blocked while the merchant has future Booked appointments
- Deactivating a Product — blocked while the product has future Booked appointments
- Removing an Estimated Service Duration from a Product Category — blocked while future Booked appointments exist for products in that category

The `appointments` table is owned by the `appointments` module. Two options were considered for how `merchant` accesses this information:

1. **Direct query** — `merchant` service classes inject `AppointmentRepository` directly.
2. **Named interface** — `appointments` exposes an `AppointmentQueryService` in its `appointments.api` `@NamedInterface` package. `merchant` depends on that interface, not on the repository.

## Decision

`AppointmentQueryService` is declared in the `appointments.api` named interface package and implemented inside the `appointments` module. The `merchant` module depends only on this interface.

## Rationale

Direct repository access from `merchant` into `appointments.persistence` is a cross-module persistence coupling that Spring Modulith's `ApplicationModules.verify()` will reject. Even if bypassed, it creates a hidden dependency that makes the modules impossible to split later. The named interface is the standard Modulith pattern for this case: it keeps the `appointments` module's internal persistence details private while giving `merchant` the minimal boolean query surface it needs.

## Consequences

- `AppointmentQueryService` in `appointments.api` declares five boolean methods scoped to the five entity types that need guarding.
- For category-level checks, the method accepts a `Set<UUID>` of product IDs rather than a category ID; the `merchant` service resolves the product IDs for a category before calling, avoiding any reverse coupling from `appointments` into merchant product data.
- `AppointmentQueryServiceImpl` in `appointments.service` implements the interface with targeted queries against `AppointmentRepository`.
- `ApplicationModules.of(...).verify()` enforces this boundary at test time.
