# Research: Keycloak Merchant & Branch Group Sync

**Feature**: 014-keycloak-merchant-groups  
**Date**: 2026-05-06

---

## Decision: Keycloak API Client Strategy

**Decision**: Spring Boot 4 declarative HTTP Interface client — `@HttpExchange` annotated interface registered via `@ImportHttpServices`.

**Rationale**: Spring Framework 7 (bundled with Spring Boot 4.0) introduced `@ImportHttpServices` (`org.springframework.web.service.registry.ImportHttpServices`), which auto-proxies annotated interfaces as Spring beans backed by either `WebClient` (reactive) or `RestClient` (imperative). This eliminates hand-rolled WebClient call chains, replaces manual `HttpServiceProxyFactory` setup, and integrates cleanly into the reactive stack using `Mono<T>` / `Flux<T>` return types. The `KeycloakGroupClient` becomes a pure interface — no implementation class, no manually constructed exchange calls. Base URL and auth filter (token refresh) are applied once via `HttpServiceGroupConfigurer`, which Spring Boot 4 calls automatically per group.

**Key Spring Boot 4 APIs**:
- `@ImportHttpServices(group, types, clientType = WEB_CLIENT)` — on a `@Configuration` class; registers proxies as beans
- `@HttpExchange` / `@GetExchange` / `@PostExchange` / `@PutExchange` / `@DeleteExchange` — on interface methods
- `HttpServiceGroupConfigurer` — `@Bean` that customises the WebClient (base URL, filters) per named group
- Return types: `Mono<T>`, `Mono<ResponseEntity<T>>`, `Mono<Void>` — fully reactive

**Alternatives considered**:
- `org.keycloak:keycloak-admin-client` (official SDK): rejected — large transitive footprint, Resteasy / blocking I/O, conflicts with WebFlux reactive model.
- Manual WebClient wrapper (hand-rolled exchange calls): rejected — superseded by Spring Boot 4's built-in HTTP Interface feature; more boilerplate with no benefit.
- `RestTemplate`: rejected — legacy, blocking, not supported on the reactive stack.

---

## Decision: Module Placement

**Decision**: New Spring Modulith sub-module `merchant.iam` at `com.elegant.software.blitzpay.merchant.iam`.

**Rationale**: Keycloak synchronization is a cross-cutting concern tied to merchant lifecycle events. It mirrors the pattern of `payments.push` (reactive side-effect listener for payment events). Placing it as a sub-module of `merchant` keeps IAM concern co-located with its domain while giving it clean internal encapsulation. It avoids polluting the core `merchant` service layer with Keycloak logic.

**Alternatives considered**:
- New top-level `iam` module: would introduce a dependency on the `merchant` module's event types from outside `merchant` — acceptable, but increases module surface for a feature that is purely reactive (read-only from `merchant`'s perspective).
- Inline into `merchant` module: rejected — mixes infrastructure (Keycloak) with core domain logic, violates single-responsibility.

---

## Decision: Merchant / Branch "Code" in Group Name

**Decision**: Use the entity's UUID as the code component in the Keycloak group name: `merchant_<uuid>` and `branch_<uuid>`.

**Rationale**: The domain model (`MerchantApplication`, `MerchantBranch`) uses UUID primary keys. There are no numeric merchant codes or branch codes in the schema. The user's example (`merchant_1001`, `branch_2001`) described the *structure* of hierarchical groups; the actual identifier token for a production group will be the UUID. The group attributes (`merchant_id`, `merchant_name`) carry the human-readable identity. Using UUIDs guarantees global uniqueness with no additional schema migration.

**Alternatives considered**:
- Add a numeric merchant code column: would require a Liquibase migration and auto-increment sequence — deferred; not in scope for this feature.
- Use `applicationReference` (e.g., `BLTZ-A1B2C3D4`): readable, but contains hyphens which require URL-encoding in Keycloak group paths; UUID is safer.

---

## Decision: Domain Event Publication Strategy

**Decision**: Add domain event publication to `MerchantRegistrationService` and `MerchantBranchService` using `ApplicationEventPublisher`. New event types in `merchant.api` named interface.

**Rationale**: The `merchant` module currently has no outbound domain events. For the IAM sync to react asynchronously, `merchant` must publish events when merchants and branches are persisted. Placing event types in `merchant.api` (a `@NamedInterface`) exposes them as the module's public contract so `merchant.iam` can consume them without accessing `merchant.internal`.

**Alternatives considered**:
- Poll the database from `merchant.iam`: rejected — tight coupling to persistence internals; violates module boundaries.
- Use Spring Data `@DomainEvents`: viable but unnecessarily couples entity to event publishing framework; service-layer publication is simpler and already used in the codebase (see `TlWebhookEventListener`).

---

## Decision: Retry / Durability Strategy

**Decision**: Spring Modulith `@ApplicationModuleListener` with the built-in Event Publication Registry for at-least-once delivery and automatic retry.

**Rationale**: The project already depends on Spring Modulith and has the `event_publication` Liquibase changeset (`20260419-001-create-event-publication.sql`). This table-backed registry persists incomplete event deliveries and retries them on application restart, giving durable async processing without additional message broker infrastructure.

**Alternatives considered**:
- Spring Retry (`@Retryable`): supports in-process retry only — does not survive application restarts; insufficient for FR-009.
- Kafka / RabbitMQ: over-engineered for a single-consumer, single-event-type listener; contradicts the simplicity principle.
- Manual retry table (`merchant_iam_sync_status`): duplicates what Modulith's Event Publication Registry already provides.

---

## Decision: Idempotency

**Decision**: GET-before-create approach: search for the group by path before attempting to create; if found, update attributes; if not, create.

**Rationale**: FR-007 requires no duplicate groups. Because the listener may be retried (at-least-once delivery), the Keycloak client must be idempotent. Keycloak's Admin REST API returns 409 Conflict on duplicate group names within the same parent; catching 409 and falling back to a GET+PUT is reliable.

**Alternatives considered**:
- Trust at-most-once delivery: rejected — the retry strategy (at-least-once) necessitates idempotency.

---

## Decision: Authentication with Keycloak Admin API

**Decision**: Service account / client credentials OAuth2 flow to obtain a short-lived admin token, cached and refreshed in the WebClient filter.

**Rationale**: Keycloak's Admin REST API requires a Bearer token from the `master` realm (or the target realm's admin service account). The client credentials flow is the standard machine-to-machine pattern; token lifetime is configurable in Keycloak. The WebClient filter refreshes the token transparently on 401 responses. No user interaction is required.

**New environment variables**:
- `KEYCLOAK_SERVER_URL` — base URL of the Keycloak server (e.g., `https://keycloak.zackpay.de`)
- `KEYCLOAK_REALM` — realm containing merchant/branch groups (e.g., `blitzpay`)
- `KEYCLOAK_ADMIN_CLIENT_ID` — admin service account client ID
- `KEYCLOAK_ADMIN_CLIENT_SECRET` — admin service account client secret

---

## Keycloak Admin REST API Endpoints Used

| Operation | Method | Path |
|-----------|--------|------|
| Get token | POST | `/realms/master/protocol/openid-connect/token` |
| Ensure root group `/merchants` | GET + POST | `/admin/realms/{realm}/groups?search=merchants&exact=true` |
| Create merchant sub-group | POST | `/admin/realms/{realm}/groups/{parentId}/children` |
| Search merchant group by name | GET | `/admin/realms/{realm}/groups?search=merchant_{id}&exact=true` |
| Update group attributes | PUT | `/admin/realms/{realm}/groups/{id}` |
| Create branch sub-group | POST | `/admin/realms/{realm}/groups/{merchantGroupId}/children` |

---

## Key Constraints (from Constitution)

- No Java source files — Kotlin only.
- New dependencies must be justified; prefer Spring ecosystem and existing libs. Rationale for no new dependency: WebClient is sufficient.
- No schema change via `ddl-auto` — all changes via Liquibase. (No new tables needed for core feature; Modulith's event publication table already exists.)
- Every behaviour change must include tests: unit tests for the sync service and Keycloak client; contract tests for new events.
- Logging via SLF4J (`LoggerFactory.getLogger(...)`) — not KotlinLogging.
- Table prefix convention: if a tracking table is added later, it must be `merchant_iam_*`.
