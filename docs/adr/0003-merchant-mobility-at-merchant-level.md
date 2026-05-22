# ADR-0003: Merchant Mobility (`MOBILE` / `BRICK_AND_MORTAR`) is a Merchant-level attribute

**Date:** 2026-05-22
**Status:** Accepted

## Context

The platform is adding support for merchants that operate from variable locations — food trucks, street sellers, circuses, pop-up theatres — rather than fixed premises. These merchants need a new operational mode (`MOBILE`) and a new concept (`Appearance`) to model their scheduled, time-windowed presence at a location.

The placement of the `MOBILE` / `BRICK_AND_MORTAR` distinction was a genuine design decision with two real options:

1. **Branch-level** — each branch independently declares its mobility type. A single merchant could have both `MOBILE` branches (delivery trucks) and a `BRICK_AND_MORTAR` branch (flagship kiosk).
2. **Merchant-level** — the entire merchant is either `MOBILE` or `BRICK_AND_MORTAR`. All branches inherit the merchant's mobility. A business with a mixed fleet must register as separate merchants.

## Decision

`Merchant Mobility` is a **Merchant-level** attribute. All branches of a `MOBILE` merchant are implicitly mobile; all branches of a `BRICK_AND_MORTAR` merchant are implicitly fixed.

## Rationale

- Offerings, products, service categories, and payment setup are all already merchant-level concerns inherited by all branches. Adding a branch-level mobility type would introduce an asymmetry with no clear benefit in the current domain.
- Geofence logic, Walk-In Ordering validation, and the `Currently Happening` API all need to know the mobility type of the branch at query time. A merchant-level attribute is simpler to propagate and reason about than a branch-level one.
- The mixed-fleet case (trucks + kiosk under one merchant) is an edge case; forcing a separate merchant registration for that scenario is a known, acceptable constraint.

## Consequences

- A business that operates both fixed locations and mobile units must register as two separate merchants on the platform.
- All proximity checks and Walk-In Ordering logic can read `Merchant Mobility` from the parent merchant without joining through every branch.
- Future branch-level mobility overrides are explicitly out of scope; this decision is a hard boundary.
