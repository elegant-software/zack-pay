# ADR-0014 — Observability architecture: programmatic Observation API over annotations

## Status
Accepted

## Context

Spring Boot 4 ships `management.observations.annotations.enabled=true`, which activates
`ObservedAspect` for the `@Observed` annotation, and auto-configures an `ObservationRegistry`
wired into `WebClient.Builder` and `RestClient.Builder`. Before deciding how to instrument
service-layer operations, we audited what Spring Boot and Micrometer actually provide
out of the box and where they fall short.

Key findings from that audit:

**`@Observed` cannot carry dynamic, per-call tag values.**
The annotation's `lowCardinalityKeyValues` and `highCardinalityKeyValues` arrays accept
only compile-time string literals. The Micrometer team closed the request for SpEL or
parameter-level resolution as "not planned" (micrometer-metrics/micrometer#4919).
Every operation in this codebase that needs `orderId`, `paymentRequestId`, or similar
identifiers attached to a span cannot use `@Observed`.

**Spring Data JPA has no native repository-method-level observation.**
Spring Data Commons 4.x does not expose a `spring.data.repository.observations-enabled`
property or equivalent. The Spring Framework observability docs explicitly warn that
annotating already-instrumented components produces duplicate observations; repositories
are not in the "already instrumented" list — they simply have no built-in Micrometer
instrumentation at the method level. JDBC-level SQL tracing requires the external
`datasource-micrometer` project, which operates at a different granularity.

**SDK integrations (Stripe, Braintree, Keycloak, S3) bypass Spring's HTTP client pipeline.**
These SDKs use their own HTTP transports (Apache HttpClient, OkHttp) rather than Spring's
`WebClient` or `RestClient`, so the auto-configured `ObservationRegistry` on `WebClient.Builder`
provides no coverage for them.

**The app is `web-application-type: servlet`, not reactive WebFlux.**
Service-layer methods execute on virtual threads, not reactive pipelines. `@Observed` +
`ObservedAspect` wraps blocking method invocations correctly. The reactive concern is
limited to outbound `WebClient` calls, where `spring.reactor.context-propagation=auto`
is required for trace context to cross reactive operator boundaries.

## Decision

### 1. Programmatic `Observation` API is the standard for service operations

`ObservabilitySupport.observe()` is the project-wide facade for creating Micrometer
observations. It wraps `Observation.createNotStarted()`, enforces the low/high cardinality
split, and filters blank high-cardinality values before attaching them. Services that need
to attach per-call identifiers (orderId, paymentRequestId, provider) use this API via a
private `observe()` helper defined inside each service.

`@Observed` is not used for service-layer tracing. It is available (enabled via
`management.observations.annotations.enabled: true`) and may be used for `@Timed` /
`@Counted` business metric annotations where dynamic tag values are not needed, but it
is not the primary tracing mechanism.

### 2. AOP aspects cover SDK integrations and repository methods

`SdkClientObservationAspect` wraps all methods on Stripe, Keycloak, and other SDK-backed
services via `@Around` pointcuts. It normalises operation names from `camelCase` to
`kebab-case` and attaches a `provider` low-cardinality tag, producing spans under the
`blitzpay.external` observation. This is cleaner than annotating each SDK method with
`@Observed` and preserves the normalisation logic in one place.

`RepositoryObservationAspect` wraps all `Repository+.*` invocations and produces spans
under `blitzpay.repository` with `repository` and `operation` low-cardinality tags. It
exists because Spring Data JPA provides no equivalent out of the box. It will be
re-evaluated if Spring Data adds native repository observation in a future release.

### 3. Business metrics use `MeterRegistry` directly

`MicrometerMerchantObservabilitySupport` (and any similar support classes) record domain
business metrics (success/failure counters) via `MeterRegistry` directly, not via the
`Observation` API. This is correct: these are custom domain counters, not general-purpose
latency observations, and they must not pollute the trace with noise.

### 4. Observation naming

| Observation name         | Covers                                          |
|--------------------------|-------------------------------------------------|
| `http.server.requests`   | Inbound HTTP — auto-instrumented by Spring MVC  |
| `http.client.requests`   | Outbound WebClient/RestClient — auto-instrumented |
| `blitzpay.external`      | SDK integrations (Stripe, Braintree, Keycloak, S3, TrueLayer) |
| `blitzpay.repository`    | Spring Data JPA repository method calls         |

### 5. Configuration additions ratified in this ADR

- `spring.reactor.context-propagation: auto` — propagates trace context through reactive
  operators used by outbound `WebClient` calls.
- `management.observations.annotations.enabled: true` — activates `ObservedAspect`; gate
  for any future `@Timed` / `@Counted` annotation use.
- `management.metrics.distribution.percentiles-histogram.blitzpay.repository: true` —
  histogram parity with `blitzpay.external`.

## Consequences

- Service code that needs per-call identifiers in spans must call `ObservabilitySupport.observe()`,
  not `@Observed`. This is non-negotiable and enforced by the limitation in Micrometer itself.
- New SDK integrations that use non-Spring HTTP transports must be added to `SdkClientObservationAspect`.
  SDK integrations that use the project's auto-configured `WebClient.Builder` or `RestClient.Builder`
  are covered automatically and must not be double-wrapped.
- Adding repository-level SQL tracing (individual query timing, bind parameters) requires
  `datasource-micrometer`; it operates at the JDBC level and complements, not replaces,
  `RepositoryObservationAspect`.
- The data policy for spans is defined in `observability/README.md`. High-cardinality keys
  must contain only allowlisted business references (order reference, payment request reference).
  Request bodies, SQL text, customer PII, and auth data must never appear in spans.
