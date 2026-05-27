# Spring Modulith Best Practices

Authoritative reference for Spring Modulith conventions in this project.
See `CONTRIBUTING.md` for the link to this document.

---

## 1. Module Boundaries

Each direct sub-package of `com.elegant.software.blitzpay` is an application module.
A module owns its API, internal implementation, persistence, and events.

```
com.elegant.software.blitzpay
├── invoice/                   ← invoice module root
│   ├── api/                   ← named interface (exposed to other modules)
│   ├── service/               ← use-case services (module-private)
│   └── config/
├── payments/
│   ├── truelayer/             ← truelayer module root
│   │   ├── api/               ← named interface
│   │   ├── inbound/
│   │   ├── outbound/
│   │   └── support/
│   └── qrpay/                 ← qrpay module root
└── config/                    ← application-level config (not a module)
```

**Rule:** Never import a type from another module's non-`api` sub-package.
Cross-module access must go through a `@NamedInterface`-annotated `api/` package.
Sub-package naming conventions are defined in [`reference/module-packaging-convention.md`](module-packaging-convention.md).

---

## 2. Module Metadata Declaration in Kotlin

Kotlin has no `package-info.java`. Declare module metadata with a dedicated annotated class.

**Pattern — module root (`@ApplicationModule`):**

```kotlin
// invoice/package-info.kt
@ApplicationModule(displayName = "Invoice")
@PackageInfo
package com.elegant.software.blitzpay.invoice
```

**Pattern — `api/` sub-package (`@NamedInterface`):**

```kotlin
// invoice/api/package-info.kt
@NamedInterface("InvoiceGateway")
@PackageInfo
package com.elegant.software.blitzpay.invoice.api
```

**Rules:**
- `@ApplicationModule` goes at the module root only; `@NamedInterface` goes in `api/` only — never mix them.
- Use `@ApplicationModule(allowedDependencies = […])` when the dependency set is stable enough to enforce.
- Other modules reference a named interface as `"invoice::InvoiceGateway"`.
- Full sub-package vocabulary and placement rules are in [`reference/module-packaging-convention.md`](module-packaging-convention.md).

**Where this is used:**
- `invoice/api/package-info.kt` → `@NamedInterface("InvoiceGateway")`
- `payments/truelayer/api/package-info.kt` → `@NamedInterface("truelayer-api")`

---

## 3. Cross-Module Communication via Events

Modules must not call each other's beans directly (except through a declared `@NamedInterface`).
For asynchronous or secondary interactions, publish domain events.

### Publishing an event

```kotlin
@Component
class PaymentRequestController(
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentUpdateBus: PaymentUpdateBus
) {
    @PostMapping("/request")
    fun createPaymentRequest(@RequestBody request: PaymentRequested): ResponseEntity<Map<String, String>> {
        val paymentRequestId = UUID.randomUUID()
        request.paymentRequestId = paymentRequestId
        eventPublisher.publishEvent(request)   // ← cross-module event
        return ResponseEntity.accepted().body(mapOf("paymentRequestId" to paymentRequestId.toString()))
    }
}
```

### Listening to an event

Use `@EventListener` for synchronous handling, or `@ApplicationModuleListener` for
transactional, async handling guaranteed to run after the publishing transaction commits.

```kotlin
// Synchronous (current pattern in this codebase)
@Component
class TrueLayerPaymentRequestListener(
    private val gateway: PaymentService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @EventListener
    fun on(e: PaymentRequested) {
        val result = gateway.startPayment(e)
        applicationEventPublisher.publishEvent(result)   // chain of events
    }
}

// Preferred for transactional correctness (use when persistence is involved)
@Component
class PaymentPersistenceListener {
    @ApplicationModuleListener
    fun on(e: PaymentRequested) {
        // runs after the publishing transaction commits
    }
}
```

**Rule:** Prefer `@ApplicationModuleListener` when the listener touches the database.
Use `@EventListener` only for in-memory / non-transactional side effects.

### Event data classes

Domain events live in the publishing module's API package and are plain data classes.
No Spring annotations on the event class itself.

```kotlin
// payments/truelayer/api/PaymentGateway.kt
data class PaymentRequested(
    var paymentRequestId: UUID?,
    val orderId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val userDisplayName: String,
    val redirectReturnUri: String
)

data class PaymentResult(
    val paymentRequestId: UUID,
    val orderId: String,
    val paymentId: String? = null,
    val redirectURI: URI? = null,
    val status: Status? = null,
    val error: String? = null
)
```

---

## 4. Module Verification Test

Every Spring Modulith project must have an architecture verification test.
This catches illegal cross-module dependencies at build time.

```kotlin
// src/test/kotlin/.../ModularityTests.kt
internal class ModularityTests {
    val modules: ApplicationModules = ApplicationModules.of(QuickpayApplication::class.java)

    @Test
    fun verifiesArchitecture() {
        modules.verify()
    }

    @Test
    fun createDocumentation() {
        Documenter(modules).writeDocumentation()
    }
}
```

Run with: `./gradlew test --tests "*.ModularityTests"`

Generated docs appear under `build/spring-modulith-docs/`. Review them after any module
boundary change.

---

## 5. `@ApplicationModuleTest` for Module Integration Tests

Test a single module in isolation — its declared dependencies are included,
the rest of the application is excluded.

```kotlin
@ApplicationModuleTest
class InvoiceModuleTest {
    @Autowired lateinit var invoiceService: InvoiceService

    @Test
    fun `generates xml`() {
        val result = invoiceService.generateXml(testInvoiceData())
        assertThat(result).isNotEmpty()
    }
}
```

This is faster than `@SpringBootTest` and verifies the module works within its declared boundary.

---

## 6. `@NamedInterface` for Sub-Package Exposure

Use `@NamedInterface` when a sub-package within a module needs to be referenced by
other modules, but the rest of the module's sub-packages should remain internal.

```kotlin
// Exposing only the `api` sub-package, not `internal` or `support`
@PackageInfo
@NamedInterface("InvoiceGateway")
class ModuleMetadata    // in invoice/api/package-info.kt
```

Other modules reference it as:
```kotlin
@ApplicationModule(allowedDependencies = ["invoice::InvoiceGateway"])
```

---

## 7. Module Smell Checklist

Before adding a cross-module dependency, check:

- [ ] Am I importing from another module's non-`api` sub-package? → move to a named interface instead
- [ ] Am I injecting a bean from another module directly? → use an event instead
- [ ] Does a module now depend on many beans from another? → reconsider the boundary
- [ ] Is a new listener doing database work? → use `@ApplicationModuleListener`, not `@EventListener`
- [ ] Did I run `modules.verify()` after changing a module boundary? → always run it

---

## References

- Spring Modulith docs: https://docs.spring.io/spring-modulith/reference/
- Application events: https://docs.spring.io/spring-modulith/reference/events.html
- Module verification: https://docs.spring.io/spring-modulith/reference/verification.html
