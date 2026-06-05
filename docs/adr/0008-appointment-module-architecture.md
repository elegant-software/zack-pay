# ADR-0008: Appointment Module Architecture

**Date:** 2026-06-05
**Status:** Accepted

The appointment booking workflow is implemented as a peer Spring Modulith module (`appointments`) with on-demand Bookable Time computation, a serialized write path, and push notifications routed through a standalone `push` module shared with payments.

## Module boundaries

`appointments` is a peer module alongside `payments.qrpay`, `payments.truelayer`, and `invoice`. It does not live inside `merchant` because the appointment lifecycle is distinct enough to justify its own persistence and API surface.

`Employee`, `Employee Availability`, `Employee Branch Assignment`, `Employee Service Assignment`, and all branch/merchant scheduling configuration live in `merchant`. `appointments` reads them via a single `@NamedInterface` service — `AppointmentSchedulingService` — which returns a `SchedulingContext` value object. This keeps the named interface surface minimal and the Bookable Time algorithm fully inside `appointments`.

## Bookable Time: on-demand computation

Bookable Time is computed on each shopper search request from the `SchedulingContext` — availability windows, opening hours, exceptions, booking policies, and existing booked appointments. No slot table is pre-materialized.

Pre-materialization was considered and rejected: it does not eliminate computation, it defers it to write time and introduces 15+ invalidation triggers (availability change, exception change, booking interval change, employee deactivation, product deactivation, etc.). On-demand computation is simpler, always consistent, and fast enough for the bounded query window (single employee + date range).

## Double-booking guard

At booking time the write path acquires a `SELECT ... FOR UPDATE` on the employee row before running the overlap check (`start_time < new_end AND end_time > new_start` on `BOOKED` appointments). This serializes concurrent bookings for the same employee without a distributed lock. A partial unique index on `(employee_id, start_time) WHERE status = 'BOOKED'` serves as a DB-level safety net.

## Appointment Reminders

A `@Scheduled` polling job fires every 5 minutes, queries `appointments WHERE status = 'BOOKED' AND shopper_account_id IS NOT NULL AND reminder_due_at <= now() AND reminder_sent = false`, sends the Expo push, and marks `reminder_sent = true`. The `reminder_due_at` column is computed at booking time as `start_time - reminder_lead_time`. In-memory `TaskScheduler` was rejected because it loses all pending reminders on restart.

## Push notifications

A standalone `push` module owns the Expo `WebClient` and `device_registration` table. Both `appointments` and `payments.qrpay` publish domain events; `push` listens via `@ApplicationModuleListener` and handles Expo delivery. This replaces the old `payments.push` module, which was payments-specific but held general push infrastructure.

## Persistence

The `appointments` module owns a single `appointments` table. Appointment Contact Details (name, email, phone, contact type) are stored inline — the relationship is mandatory, singular, and never queried independently of the appointment.
