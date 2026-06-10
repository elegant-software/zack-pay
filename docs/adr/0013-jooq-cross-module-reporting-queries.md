# ADR-0013 — jOOQ as the cross-module read layer for reporting

## Status
Accepted

## Context

The `reporting` module produces aggregate KPI snapshots that span data owned by multiple application modules: `order` (orders, payment attempts), `appointments` (appointments), and `payments` (payment settlements). Spring Modulith enforces module isolation through Spring bean dependency checks — a module may not inject beans from another module's `internal` package.

A naive solution is to add a `@NamedInterface` gateway on each module (e.g. `AppointmentKpiGateway`) and call it from `reporting` as a bean dependency. This preserves isolation at the cost of boilerplate: every new KPI dimension needs a new interface, a new implementation, and a new `@ApplicationModuleTest`.

## Decision

The `reporting` module uses **jOOQ** to query across module-owned tables directly. jOOQ's generated table and field references (e.g. `Tables.APPOINTMENTS`, `Tables.ORDER_ORDERS`) live in the shared `jooq` package — not inside any module's package — so Modulith's `verify()` check does not flag them as boundary violations.

This establishes the rule: **Spring Modulith boundaries govern write-side bean dependencies and event contracts; jOOQ is the sanctioned read-side tool for cross-module projections in the `reporting` module.**

All other cross-module communication (writes, commands, side-effects) must still use published domain events or named interfaces.

## Consequences

- `reporting` queries any table it needs without gateway indirection, keeping KPI queries as single SQL statements with joins.
- Adding a new KPI dimension (e.g. appointment cancellation rate) requires only a new jOOQ column reference, not a new interface and listener.
- The `reporting` module has an implicit structural dependency on the database schema of other modules. Schema changes (column renames, table moves) break reporting queries at compile time via jOOQ's generated code — this is acceptable and visible.
- Read-side projections in `reporting` must never trigger writes or side-effects against other modules' data.
