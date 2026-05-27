# Module Packaging Convention

This document defines the canonical sub-package layout for every Spring Modulith module in
`com.elegant.software.blitzpay`. It derives from patterns already present across the codebase,
resolves inconsistencies, and calls out gaps. Follow this layout for new modules and migrate
existing ones incrementally.

---

## Core principle: describe content, not visibility

Spring Modulith already enforces module boundaries at the framework level. Only packages
annotated with `@NamedInterface` in `api` are reachable from other modules — everything else
is private by default, regardless of what the package is called.

Given that, naming a package `internal` adds no information. It just says "this is private",
which Modulith already guarantees. Every non-`api` package should instead be named after
**what it contains**, making the module structure self-documenting.

---

## Package vocabulary

| Package | Purpose | Who may depend on it |
|---------|---------|----------------------|
| `api` | Public module surface: interfaces annotated with `@NamedInterface`, domain events, request/response DTOs used across module boundaries. Nothing in `api` may import from any other sub-package. | Any module |
| `web` | HTTP controllers that expose the module's capabilities to **external clients** (browsers, mobile apps). | Module-private |
| `inbound` | Adapters for traffic **arriving from external systems** (webhooks, third-party callbacks). Used in provider-adapter modules where direction is meaningful. | Module-private |
| `outbound` | Adapters for calls **going to external systems** (provider SDKs, third-party APIs). Symmetric counterpart to `inbound`. | Module-private |
| `service` | Service layer: use cases, orchestration, projections, validators, scheduled jobs, SDK client wrappers. The standard home for all business logic. | Module-private |
| `listener` | Event listeners (`@ApplicationModuleListener`, `@EventListener`). Use when a module has enough listeners to warrant their own home. | Module-private |
| `persistence` | Parent package for all DB-related types. Always split into two sub-packages: `persistence/model` for JPA entities and `persistence/repository` for Spring Data interfaces. Entities are plain data holders; all logic lives in `service`. | Module-private |
| `config` | `@Configuration` beans and `@ConfigurationProperties` classes. One package per module; no business logic. | Module-private |
| `security` | Signature verification, JWKS fetching, auth token helpers. Separate from `config` to make the security boundary explicit and auditable. | Module-private |
| `support` | Genuine infrastructure cross-cuts that do not fit any of the above: reactive buses, observability helpers, audit trails. Keep small — resist using it as a catch-all. | Module-private, or explicitly shared within a parent package |
| `mcp` | Spring AI `@McpTool` definitions. Present only in modules that expose MCP tools. | Module-private |

---

## Canonical layout by module type

### Domain-owning module (e.g. `merchant`, `order`)

```
<module>/
  api/                    ← @NamedInterface contracts, events, cross-module DTOs
  service/                ← use-case services, projections, validators
  persistence/
    model/                ← JPA entities (plain data holders)
    repository/           ← Spring Data interfaces
  web/                    ← REST controllers for external clients
  config/                 ← @Configuration + @ConfigurationProperties
  support/                ← observability helpers, audit trails (keep minimal)
```

### Payment provider adapter (e.g. `truelayer`, `stripe`, `braintree`)

```
<module>/
  api/            ← gateway interface + request/result models (if cross-module)
  inbound/        ← webhook / callback controllers and envelope models
  outbound/       ← SDK adapter, payment initiation service
  config/         ← SDK bean wiring + properties
  security/       ← signature verification, JWKS fetching, token helpers
```

`inbound`/`outbound` names are load-bearing here: they signal that inbound traffic requires
signature verification before processing and outbound calls require authentication and
resilience. These are different safety obligations and deserve distinct packages.

### Cross-cutting infrastructure module (e.g. `payments.push`)

```
<module>/
  api/                    ← public gateways, events, response DTOs
  service/                ← services, dispatchers, polling jobs, SDK clients
  listener/               ← @ApplicationModuleListener and @EventListener handlers
  persistence/
    model/                ← JPA entities
    repository/           ← Spring Data interfaces
  config/                 ← @Configuration + @ConfigurationProperties
```

### Thin adapter / utility module (e.g. `voice`, `storage`, `invoice`)

```
<module>/
  api/            ← gateway interface (if cross-module contract exists)
  service/        ← implementation, SDK client wrappers
  web/            ← REST controller (if client-facing)
  config/         ← @Configuration + @ConfigurationProperties
```

---

## DTO placement

DTOs are not a package — they follow the scope of whatever uses them.

| DTO type | Package | Rationale |
|----------|---------|-----------|
| Cross-module contracts: events, gateway request/result models, shared response shapes | `api/` | Other modules must import them; they are part of the public surface |
| HTTP request/response bodies used only by a controller | `web/` | Co-locate with the controller that owns them; no other package needs them |
| Provider envelope models (e.g. `TlWebhookEnvelope`) | `inbound/` | Only the inbound adapter deserialises them; they must not leak to other packages |
| Internal data-passing shapes between services within the same module | `service/` | Module-private; elevating them to `api` would widen the public surface unnecessarily |

**Rule:** if a DTO is used in more than one package within a module, it moves to the nearest common ancestor. If that ancestor would be `api`, confirm that other modules genuinely need it before promoting it.

---

## Exception placement

Like DTOs, exceptions follow their scope.

| Exception type | Package | Rationale |
|----------------|---------|-----------|
| Thrown across module boundaries (e.g. a gateway that other modules catch) | `api/` | Must be visible to callers outside the module |
| Thrown and caught only within a single module | `service/` | Module-private; keep it next to the logic that throws it |
| Thrown by a controller for HTTP-specific error cases | `web/` | Lives with the controller; a `@ControllerAdvice` in `web/` handles it |

`@ControllerAdvice` / `@ExceptionHandler` classes belong in `web/` — they are part of the HTTP-layer concern, not the service layer.

---

## Enum placement

Enums follow the same scope rules as DTOs and exceptions.

| Enum type | Package |
|-----------|---------|
| Shared across module boundaries (`PaymentStatusCode`, `PaymentMethod`) | `api/` |
| Describes the state of a JPA entity within one module (`OrderStatus`) | `persistence/model/` alongside the entity |
| Used only by services within one module | `service/` |

---

## Module metadata declarations

Kotlin has no `package-info.java`. Use a dedicated `@PackageInfo`-annotated class instead. Two placement rules apply:

### Module root — `@ApplicationModule`

Place `@ApplicationModule` at the module root (`<module>/package-info.kt`). This is where you declare the module's display name and, when the dependency set is stable enough to lock down, its `allowedDependencies`.

```kotlin
// merchant/package-info.kt
@ApplicationModule(
    displayName = "Merchant",
    allowedDependencies = ["order::OrderApi", "payments.push::PushApi"]
)
@PackageInfo
package com.elegant.software.blitzpay.merchant
```

### `api/` sub-package — `@NamedInterface`

Place `@NamedInterface` inside the `api/` package (`<module>/api/package-info.kt`). This exposes the sub-package as a named contract that other modules can reference without gaining access to the rest of the module.

```kotlin
// merchant/api/package-info.kt
@NamedInterface("MerchantApi")
@PackageInfo
package com.elegant.software.blitzpay.merchant.api
```

Other modules then reference it as:

```kotlin
@ApplicationModule(allowedDependencies = ["merchant::MerchantApi"])
```

**Rules:**
- Never annotate `@NamedInterface` at the module root — it belongs on the `api/` sub-package.
- Do not annotate `@ApplicationModule` inside a sub-package — it belongs at the module root.
- Add `allowedDependencies` only when the dependency set is stable; an empty list locks the module to zero cross-module dependencies, which is often too strict early on.

---

## Domain events

Domain events are cross-module signals. They live in the **publishing** module's `api/` package so consuming modules can import them without breaching module boundaries.

```kotlin
// payments/truelayer/api/PaymentEvents.kt
data class PaymentCompleted(val paymentRequestId: UUID, val orderId: String)
```

**Rules:**
- Events are plain data classes. No Spring annotations on the event class itself.
- Publish via `ApplicationEventPublisher`; consume via `@ApplicationModuleListener` (preferred when the listener touches the database) or `@EventListener` (in-memory / non-transactional side effects only).
- See [`reference/spring-modulith-best-practices.md`](spring-modulith-best-practices.md) for the full event pattern and listener guidance.

---

## Module verification

Every module boundary change must be followed by running the architecture verification test:

```bash
./gradlew test --tests "*.ModularityTests"
```

The test calls `ApplicationModules.of(...).verify()` and fails on any illegal cross-module dependency. Do not merge a module boundary change without a green verification run.

`@ApplicationModuleTest` isolates a single module for integration testing — its declared dependencies are bootstrapped, the rest of the application is excluded. Prefer it over `@SpringBootTest` for module-level tests.

See [`reference/spring-modulith-best-practices.md`](spring-modulith-best-practices.md) for the full verification and testing patterns.

---

## Current state and gaps

### `payments.truelayer` — `support` should split

`support` currently holds two distinct concerns:

| File | Correct package |
|------|----------------|
| `TrueLayerConfig.kt` | `config` |
| `TrueLayerProperties.kt` | `config` |
| `TlWebhookProperties.kt` | `config` |
| `JwksService.kt` | `security` |
| `TlSignatureVerifier.kt` | `security` |

Move config beans to `config` (the package already exists) and introduce `security` for
JWKS and signature concerns.

### `payments.stripe` and `payments.braintree` — rename `internal` to `inbound`/`outbound`

| Module | Current | Recommended |
|--------|---------|-------------|
| `payments.stripe` | `internal/StripePaymentController` | `web` (client-facing initiation) |
| `payments.stripe` | `internal/StripeWebhookController` | `inbound` |
| `payments.stripe` | `internal/StripePaymentService` | `outbound` |
| `payments.braintree` | `internal/BraintreePaymentController` | `web` + `outbound` (sync result) |
| `payments.braintree` | `internal/BraintreePaymentService` | `outbound` |

### `payments.push` — rename `internal` to descriptive packages

| Current | Recommended | Reason |
|---------|-------------|--------|
| `internal/PaymentStatusService.kt` | `service/` | core status state machine |
| `internal/PushDispatcher.kt` | `service/` | orchestration service |
| `internal/DeviceRegistrationService.kt` | `service/` | registration management |
| `internal/TlWebhookEventListener.kt` | `listener/` | event handler |
| `internal/PaymentStatusChangedListener.kt` | `listener/` | event handler |
| `internal/ExpoReceiptPoller.kt` | `service/` | scheduled job |
| `internal/ExpoPushClient.kt` | `service/` | Expo HTTP client |
| `internal/PaymentStatusUpdateGatewayImpl.kt` | `service/` | gateway implementation |
| `internal/PaymentVoiceContextService.kt` | `service/` | gateway implementation |

### `voice` — rename `internal` to descriptive packages

| Current | Recommended |
|---------|-------------|
| `internal/VoiceQueryService.kt` | `service/` |
| `internal/SpeechTranscriptionClient.kt` | `service/` |
| `internal/WhisperSpeechTranscriptionClient.kt` | `service/` |
| `internal/VoiceModels.kt` | `service/` (or `web/` if only used by the controller) |
| `internal/VoiceExceptions.kt` | `service/` |

### `payments.qrpay` — flat structure

All classes sit at the module root with no sub-packages:

| File | Correct package |
|------|----------------|
| `PaymentRequestController.kt` | `web` |
| `QrPaymentSseController.kt` | `web` |
| `PaymentInitRequestListener.kt` | `listener` |
| `PaymentUpdateBus.kt` (currently in `payments.support`) | `support` (owned by qrpay) |

### `payments.support` — unclear ownership

`PaymentUpdateBus.kt` is exclusively used by `payments.qrpay` and should move to
`payments.qrpay/support`. The `payments.support` package can then be retired.

### `merchant` and `order` — rename `application` → `service`, collapse `domain` + `repository` → `persistence`

Both modules use `application/` for services and split entities across `domain/` + `repository/`.
Migrate to:

| Current | Recommended |
|---------|-------------|
| `merchant/application/` | `merchant/service/` |
| `merchant/domain/` | `merchant/persistence/model/` |
| `merchant/repository/` | `merchant/persistence/repository/` |
| `order/application/` | `order/service/` |
| `order/domain/` | `order/persistence/model/` |
| `order/repository/` | `order/persistence/repository/` |

---

## Rules

1. **`api` packages are the only cross-module import target.** No code outside a module may
   import from any non-`api` sub-package.

2. **Do not name packages after visibility.** Modulith enforces module privacy. Use names that
   describe what is in the package (`service`, `listener`, `web`, `inbound`, `outbound`), not
   that it is hidden (`internal`).

3. **Entities are plain data holders.** No business logic in JPA entities. Logic belongs in
   `service`. Entities live in `persistence/model`, repositories in `persistence/repository`.

4. **`config` holds no business logic.** If a bean in `config` does more than wire dependencies
   or bind properties, move it to the appropriate descriptive package.

5. **`security` is mandatory for any module that verifies external signatures or manages
   credentials.** Do not bury signature verification inside `support` or `inbound`.

6. **Controllers that receive external traffic belong in `web` (client-facing) or `inbound`
   (provider webhooks/callbacks).** The package name signals the traffic source and the
   verification obligation that comes with it.

7. **`persistence` entities are owned by exactly one module.** This mirrors the table-prefix
   rule in `CONSTITUTION.md`. Do not read another module's persistence layer directly.

8. **`support` is a last resort.** If a utility belongs in `config`, `security`, or `service`,
   it goes there. `support` is for genuine infrastructure cross-cuts (reactive buses,
   observability, audit) that do not fit a more descriptive name.
