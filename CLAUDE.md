# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- Kotlin 2.3.20 on Java 25, Spring Boot 4.0.4, Spring WebFlux (reactive)
- Spring Modulith for module enforcement and cross-module event publishing
- PostgreSQL 16 with Hibernate (`ddl-auto: none`) + Liquibase for all schema migrations
- TrueLayer Java SDK for payment gateway integration
- Mustang Project + Flying Saucer for ZUGFeRD/Factur-X invoice generation

## Commands

```bash
./gradlew clean build        # full build including all tests
./gradlew test               # unit tests only
./gradlew contractTest       # API contract suite (WebTestClient, no real DB or TrueLayer)
./gradlew check              # unit tests + contract tests
./gradlew bootRun            # run app (requires env vars below)
```

Run a single test class:
```bash
./gradlew test --tests "com.elegant.software.blitzpay.invoice.InvoiceControllerTest"
```

## Required Environment Variables

```
TRUELAYER_CLIENT_ID
TRUELAYER_CLIENT_SECRET
TRUELAYER_KEY_ID
TRUELAYER_MERCHANT_ACCOUNT_ID
TRUELAYER_PRIVATE_KEY_PATH   # path to PEM private key
EXPO_ACCESS_TOKEN            # Expo Push access token (payments.push module)
KEYCLOAK_IAM_SERVER_URL      # Keycloak server base URL (merchant.iam module)
KEYCLOAK_IAM_REALM           # Keycloak realm for merchant/branch groups
KEYCLOAK_IAM_ADMIN_CLIENT_ID # service account client ID with realm-management roles
KEYCLOAK_IAM_ADMIN_CLIENT_SECRET # service account client secret
```

Contract tests do **not** require these — TrueLayer and Keycloak beans are mocked under the `contract-test` Spring profile.

## Architecture

The app uses **Spring Modulith**: each direct sub-package of `com.elegant.software.blitzpay` is an application module owning its API, internal implementation, persistence, and events.

### Modules

- **`payments.truelayer`** — TrueLayer gateway integration. Inbound: `WebhookController` receives payment status callbacks and verifies TrueLayer signatures. Outbound: `PaymentService` initiates payments. Public surface: `PaymentGateway` interface.
- **`payments.qrpay`** — QR-code payment requests. `PaymentRequestController` creates payment requests; `QrPaymentSseController` streams status updates via SSE. Listens for `PaymentInitRequest` events and publishes results via `PaymentUpdateBus`.
- **`invoice`** — EU-standard ZUGFeRD/Factur-X invoice generation. `InvoiceController` routes by `Accept` header to XML or PDF generation. Public surface: `InvoiceGateway` / `InvoiceService`.

### Cross-module communication

Modules communicate via Spring `ApplicationEventPublisher` / `@ApplicationModuleListener`. Direct bean coupling across modules is an architecture smell — prefer published domain events for async interactions. Named interfaces (`@NamedInterface`) are used to expose sub-package contracts without making entire `internal` packages public.

### API versioning

URL-path versioning (`/v1/...`) uses a custom `PathOnlyApiVersionResolver` that prevents version parsing collisions with Swagger/documentation routes. Config: `useVersionRequired(false)`, `setDefaultVersion("1")`, `detectSupportedVersions(true)`. See `reference/api-versioning-guide.md` for details.

### Module metadata

Because Kotlin lacks `package-info.java`, module metadata is declared via a dedicated type annotated with `@org.springframework.modulith.PackageInfo`. Use this for `@ApplicationModule` and `@NamedInterface` declarations.

## CORS / Frontend Origin
`blitzpay.cors.allowed-origins` comes from `FRONTEND_ORIGIN`: set via `config.frontendOrigin` in `helm/blitz-pay/values-*.yaml` (k8s) and in `infra/lib/app-stack.ts` (AWS). Whenever the admin dashboard's domain changes, update these to match, or every browser call fails with a 403 on the CORS preflight. Full cross-repo checklist: `CLAUDE.md` in `blitzpay-admin-dashboard`, section "Changing Domains / URLs".

## Testing

- **Unit tests** (`src/test/kotlin`) — JUnit 5 + Mockito Kotlin. JSON fixtures in `src/test/resources/testdata/` loaded via `TestFixtureLoader`.
- **Contract tests** (`src/contractTest/kotlin`) — handwritten `WebTestClient` tests. The `contract-test` profile excludes DataSource, JPA, and Modulith event persistence auto-configuration; TrueLayer beans are mocked. Groovy contracts live in `src/contractTest/resources/contracts/`.
- **Module verification** — maintain `ApplicationModules.of(...).verify()` tests. Use `@ApplicationModuleTest` for single-module integration tests.

## Contributor Guide

**[`CONSTITUTION.md`](CONSTITUTION.md)** is the governing rules document — read it before making any change. It contains non-negotiable conventions for schema, modules, testing, security, and code style, plus a full index of all `reference/` best-practice documents.

Full contribution workflow conventions (commit style, CI, PR process) are in **`CONTRIBUTING.md`**.

## Commit Convention

Semantic commits: `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`. Summaries: short, imperative, specific. One semantic commit per logical change; squash fixup commits before merging.

## Active Technologies
- Kotlin 2.3.20 on Java 25 (unchanged) + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA on PostgreSQL 16, TrueLayer Java SDK (unchanged). New: Reactor `WebClient` against the Expo Push HTTPS API (`https://exp.host/--/api/v2/push/send`) — no additional SDK; a thin in-repo client keeps the dependency surface minimal. (006-push-notifications)
- PostgreSQL via JPA (`ddl-auto: none`) + Liquibase. Two new tables: `payment_status` (authoritative current state per payment request) and `device_registration` (Expo push tokens per payment request / payer). Optional `push_delivery_attempt` for observability; kept in-memory-only if retention cost is a concern. (006-push-notifications)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA, `stripe-java` 28.x, `braintree-java` 3.43.0 (001-merchant-onboarding)
- PostgreSQL 16 — `ddl-auto: none` + Liquibase; two new tables (`merchant_branches`), two modified tables (`merchant_applications`, `merchant_products`) (001-merchant-onboarding)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (reactive controllers with `Mono` wrappers), Spring Modulith, Hibernate/JPA, Liquibase (008-geofence-nearby-merchants)
- PostgreSQL 16 (`blitzpay` schema), `ddl-auto: none` — all schema changes via Liquibase (008-geofence-nearby-merchants)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith, Hibernate/JPA, Spring AI (MCP `@McpTool`) (feat/merchant-mcp)
- PostgreSQL 16 (`blitzpay` schema), `ddl-auto: none`, Liquibase for all schema changes (feat/merchant-mcp)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA, Liquibase (main)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (WebClient), Spring Modulith 2.1.0-M3 (014-keycloak-merchant-groups)
- No new tables; existing `event_publication` Modulith table for durability (014-keycloak-merchant-groups)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith 2.1.0-M3, Spring Framework 7 HTTP Interfaces (`@HttpExchange`, `@ImportHttpServices`) (014-keycloak-merchant-groups)
- No new tables; existing `event_publication` Modulith table for at-least-once delivery (014-keycloak-merchant-groups)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith 2.1.0-M3, Spring AI (`@McpTool`), Hibernate/JPA (feat/014-keycloak-merchant-groups)
- PostgreSQL 16 (`blitzpay` schema) — no new tables or columns (feat/014-keycloak-merchant-groups)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux (reactive), Spring Modulith 2.1.0-M3, Hibernate/JPA, Liquibase (018-deferred-payment-order-apis)

## Recent Changes
- 006-push-notifications: Added Kotlin 2.3.20 on Java 25 (unchanged) + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Hibernate/JPA on PostgreSQL 16, TrueLayer Java SDK (unchanged). New: Reactor `WebClient` against the Expo Push HTTPS API (`https://exp.host/--/api/v2/push/send`) — no additional SDK; a thin in-repo client keeps the dependency surface minimal.
