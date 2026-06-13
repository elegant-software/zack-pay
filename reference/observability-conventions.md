# Observability Conventions

Authoritative reference for how blitz-pay instruments service code with metrics, traces,
and structured logs. Read this before adding any observation, metric, or span to a new
or existing service. The architectural rationale is in
[`docs/adr/0014-observability-architecture.md`](../docs/adr/0014-observability-architecture.md).

---

## 1. Observation API vs `@Observed` annotation

**Use `ObservabilitySupport.observe()` for all service-layer operations.**

`@Observed` is enabled (`management.observations.annotations.enabled: true`) but is
deliberately not the primary instrumentation mechanism. The Micrometer team closed the
request for dynamic (runtime) tag values in `@Observed` as not planned. Any operation
that attaches a per-call identifier — `orderId`, `paymentRequestId`, `provider` — cannot
use the annotation.

```kotlin
// Correct: programmatic, carries runtime identifiers
private fun <T> observe(operation: String, orderId: String, requestId: String?, block: () -> T): T =
    observability.observe(
        name = "blitzpay.external",
        contextualName = "stripe $operation",
        lowCardinality = mapOf("provider" to "stripe", "operation" to operation),
        highCardinality = mapOf("order.reference" to orderId, "payment.request.reference" to requestId),
        block = block,
    )

// Wrong: static values only, cannot pass orderId at call time
@Observed(name = "blitzpay.external", lowCardinalityKeyValues = ["provider", "stripe"])
fun createIntent(...): StripeIntentResult { ... }
```

`@Observed` is suitable only for operations where all tag values are static and known at
compile time. Currently there are no such cases in production service code.

---

## 2. Observation naming

| Name | Covers | Produced by |
|------|--------|-------------|
| `http.server.requests` | Inbound HTTP | Spring MVC auto-instrumentation |
| `http.client.requests` | Outbound WebClient / RestClient | Spring auto-instrumentation via `ObservationRegistry` on builder |
| `blitzpay.external` | SDK integrations (Stripe, Braintree, Keycloak, S3, TrueLayer) | `ObservabilitySupport` + `SdkClientObservationAspect` |
| `blitzpay.repository` | Spring Data JPA repository method calls | `RepositoryObservationAspect` |

Do not invent new top-level names. New SDK integrations go under `blitzpay.external` with
a `provider` tag. New repository methods are covered automatically. New first-class domains
(e.g., a message broker) may warrant a new name but require a discussion and an ADR update.

---

## 3. Tag cardinality rules

**Low-cardinality keys** appear in both metrics and traces. They must have bounded value
sets (< ~100 possible values). Examples: `provider`, `operation`, `status`.

**High-cardinality keys** appear in traces only, never in metrics. Use them for per-request
identifiers. The data policy below defines what is permitted.

```kotlin
observability.observe(
    name = "blitzpay.external",
    contextualName = "s3 upload",
    lowCardinality = mapOf("provider" to "s3", "operation" to "upload"),
    highCardinality = mapOf("order.reference" to orderId),   // traces only
    block = block,
)
```

`ObservabilitySupport.observe()` automatically drops high-cardinality entries whose value
is null or blank — no need to guard at the call site.

---

## 4. Pattern: per-service `observe()` helper

Each service that wraps operations in observations defines a private `observe()` method
that fixes the name, contextualName prefix, and low-cardinality tags for that service,
then delegates to `ObservabilitySupport`. This keeps call sites readable.

```kotlin
@Service
class PaymentService(
    private val observability: ObservabilitySupport,
    // ...
) {
    fun createPayment(orderId: String, requestId: String) {
        val result = observe("create-payment", orderId, requestId) {
            gateway.initiate(...)
        }
    }

    private fun <T> observe(operation: String, orderId: String, requestId: String, block: () -> T): T =
        observability.observe(
            name = "blitzpay.external",
            contextualName = "truelayer $operation",
            lowCardinality = mapOf("provider" to "truelayer", "operation" to operation),
            highCardinality = mapOf("order.reference" to orderId, "payment.request.reference" to requestId),
            block = block,
        )
}
```

---

## 5. SDK integrations — `SdkClientObservationAspect`

Services that use an SDK with a non-Spring HTTP transport (Stripe, Braintree, Keycloak)
are automatically wrapped by `SdkClientObservationAspect`. The aspect:

- creates an observation named `blitzpay.external`
- sets `contextualName` to `"{provider} {operation}"` where operation is derived from the
  method name in `kebab-case`
- attaches `provider` and `operation` as low-cardinality tags

If you add a new SDK-backed service that bypasses `WebClient`/`RestClient`, add its
class to the `@Around` pointcut in `SdkClientObservationAspect`. Do not also wrap its
callers with a manual `observe()` call — that would produce nested duplicate spans.

Services that use the project's auto-configured `WebClient.Builder` or `RestClient.Builder`
(`HttpClientConfiguration`) are covered automatically by Spring's HTTP client instrumentation.
Do not add a manual `observe()` call around those — it would produce a redundant parent span.

---

## 6. Repository methods — `RepositoryObservationAspect`

All `Repository+.*` invocations are automatically wrapped with an observation named
`blitzpay.repository` carrying `repository` (simple class name) and `operation` (method name)
tags. No annotation or explicit call is required.

Spring Data JPA does not provide native method-level observations. This aspect fills that
gap. Do not add `@Observed` to repository interfaces — it would produce duplicate spans.

---

## 7. Business metrics — `MeterRegistry` direct

Domain-level event counters (success/failure counts per business action) use `MeterRegistry`
directly, not the `Observation` API. Model after `MicrometerMerchantObservabilitySupport`:

```kotlin
@Component
class FooObservabilitySupport(private val meterRegistry: MeterRegistry) {
    fun recordSuccess(action: String) {
        meterRegistry.counter("foo.actions", "action", action, "status", "success").increment()
    }
    fun recordFailure(action: String, reason: String) {
        meterRegistry.counter("foo.failures", "action", action, "reason", reason).increment()
    }
}
```

These counters appear in Prometheus/Grafana and are separate from trace spans. They do not
get a `blitzpay.*` observation name — use a domain-prefixed metric name like `merchant.*`
or `order.*`.

---

## 8. Structured logging correlation

Use `LogContext` constants for MDC keys. Never use raw string literals.

```kotlin
import com.elegant.software.blitzpay.config.LogContext

MDC.put(LogContext.ORDER_ID, orderId)
MDC.put(LogContext.PAYMENT_REQUEST_ID, paymentRequestId)
```

`traceId` and `spanId` are injected automatically by the Micrometer Tracing / Logback
bridge. Do not set them manually.

Log lines that describe the outcome of an observed operation should be placed inside the
`observe()` lambda so that the MDC trace context is active.

---

## 9. Data policy — what must not appear in spans

Spans are exported to Grafana Tempo and may be retained by the OpenTelemetry collector.
The following must never be attached as key values or event data:

- Request or response bodies
- Authorization headers, tokens, API keys
- Customer name, email, phone, address, or any PII
- SQL query text or bind parameter values
- Arbitrary query string parameters

Permitted high-cardinality keys: `order.reference`, `payment.request.reference`.
These are opaque business identifiers, not customer data.

---

## 10. Reactive context propagation

`spring.reactor.context-propagation: auto` is set globally. This ensures trace context
flows through Reactor operators used by outbound `WebClient` calls. No per-call
configuration is required. If a new reactive pipeline is introduced that does not
propagate the context, check whether it creates its own `Scheduler` — custom schedulers
need `Schedulers.onScheduleHook` or a `ContextPropagatingTaskDecorator`.
