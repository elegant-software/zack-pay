# HTTP Interface Clients — Best Practices

Spring Boot 4 / Spring Framework 7 ships a declarative HTTP client model built on `@HttpExchange` interfaces and registered via `@ImportHttpServices`. This guide is the project standard for any code that calls an external HTTP API.

---

## 1. When to Use `@HttpExchange` vs Raw `WebClient`

| Situation | Use |
|-----------|-----|
| Calling a well-defined external REST API (Keycloak, Stripe, internal service) | `@HttpExchange` interface |
| One-off, highly dynamic URL construction | Raw `WebClient` |
| Token endpoint or other pre-auth call *needed to bootstrap* the interface client | Raw `WebClient` (separate bean) |

**Rule**: if the API has a stable base URL and fixed path structure, define an interface. Do not wrap `WebClient` in a service class when an interface suffices.

---

## 2. Declaring the Interface

```kotlin
@HttpExchange("/groups")                          // base path, appended to the group's baseUrl
interface KeycloakGroupClient {

    @GetExchange
    fun searchGroups(
        @RequestParam search: String,
        @RequestParam exact: Boolean,
    ): Mono<List<KeycloakGroupRepresentation>>

    @PostExchange
    fun createGroup(
        @RequestBody body: KeycloakGroupBody,
    ): Mono<ResponseEntity<Void>>

    @GetExchange("/{parentId}/children")
    fun getChildren(
        @PathVariable parentId: String,
        @RequestParam search: String,
        @RequestParam exact: Boolean,
    ): Mono<List<KeycloakGroupRepresentation>>

    @PostExchange("/{parentId}/children")
    fun createChildGroup(
        @PathVariable parentId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<ResponseEntity<Void>>

    @PutExchange("/{groupId}")
    fun updateGroup(
        @PathVariable groupId: String,
        @RequestBody body: KeycloakGroupBody,
    ): Mono<Void>
}
```

**Rules**:
- Interface only — no `abstract class`, no implementation class.
- Use `Mono<ResponseEntity<Void>>` for `POST`/`PUT` calls that return a `Location` header or a status code only.
- Use `Mono<T>` for calls that return a JSON body.
- Do not add default parameter values (`= true`) to `@HttpExchange` methods — Spring's proxy cannot pass them.

---

## 3. Registering with `@ImportHttpServices`

```kotlin
@Configuration
@Profile("!contract-test")                        // exclude in contract tests — no real external API
@EnableConfigurationProperties(KeycloakProperties::class)
@ImportHttpServices(
    group = "keycloak-admin",
    types = [KeycloakGroupClient::class],
    clientType = HttpServiceGroup.ClientType.WEB_CLIENT,   // reactive; use REST_CLIENT for blocking
)
class KeycloakWebClientConfig {

    @Bean
    fun keycloakGroupConfigurer(
        properties: KeycloakProperties,
        tokenProvider: KeycloakTokenProvider,
    ): HttpServiceGroupConfigurer = HttpServiceGroupConfigurer { groups ->
        groups.filterByName("keycloak-admin").forEachClient { _, builder ->
            builder.baseUrl("${properties.serverUrl}/admin/realms/${properties.realm}")
            builder.filter(KeycloakBearerTokenFilter(tokenProvider))
        }
    }
}
```

**Rules**:
- Always add `@Profile("!contract-test")` to the `@Configuration` class so that contract tests do not require a live external endpoint. Contract tests mock the interface directly.
- `group` is a logical name; `filterByName(group)` in `HttpServiceGroupConfigurer` must match exactly.
- Use `ClientType.WEB_CLIENT` for reactive (`Mono`/`Flux`) interfaces. Use `ClientType.REST_CLIENT` for blocking ones.
- One `@ImportHttpServices` per external API group. Do not mix interfaces from different services in the same group.

---

## 4. Configuration Properties

```kotlin
@ConfigurationProperties(prefix = "keycloak.iam")
data class KeycloakProperties(
    val serverUrl: String,
    val realm: String,
    val adminClientId: String,
    val adminClientSecret: String,
)
```

Bind in `application.yml`:
```yaml
keycloak:
  iam:
    server-url: ${KEYCLOAK_IAM_SERVER_URL:https://keycloak.example.com}
    realm: ${KEYCLOAK_IAM_REALM:blitzpay-business}
    admin-client-id: ${KEYCLOAK_IAM_ADMIN_CLIENT_ID:}
    admin-client-secret: ${KEYCLOAK_IAM_ADMIN_CLIENT_SECRET:}
```

Document every new env var in `CLAUDE.md` under **Required Environment Variables**.

---

## 5. Token Management

When an external API requires a bearer token obtained via a separate endpoint (e.g., OAuth2 client credentials), do **not** add the token call to the `@HttpExchange` interface. Instead:

### `KeycloakTokenProvider` — thread-safe cache

```kotlin
@Component
class KeycloakTokenProvider(private val properties: KeycloakProperties) {

    private val tokenClient: WebClient = WebClient.create()       // plain WebClient for token endpoint
    private val cachedToken = AtomicReference<CachedToken?>()

    fun getToken(): Mono<String> {
        val cached = cachedToken.get()
        if (cached != null && !cached.isExpired()) return Mono.just(cached.value)
        return fetchToken().doOnNext { cachedToken.set(it) }.map { it.value }
    }

    fun invalidate() { cachedToken.set(null) }

    private fun fetchToken(): Mono<CachedToken> { /* POST to token endpoint */ }
}
```

**Rules**:
- Cache tokens; re-use until `expires_in - 30` seconds to avoid races at expiry.
- `invalidate()` clears the cache (called on 401).
- Use a **separate** `WebClient` instance for the token endpoint, not the HTTP Interface client.

### `KeycloakBearerTokenFilter` — inject and refresh

```kotlin
class KeycloakBearerTokenFilter(private val tokenProvider: KeycloakTokenProvider) : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        tokenProvider.getToken().flatMap { token ->
            val authedRequest = ClientRequest.from(request)
                .headers { it.setBearerAuth(token) }
                .build()
            next.exchange(authedRequest).flatMap { response ->
                if (response.statusCode().value() == 401) {
                    tokenProvider.invalidate()
                    response.releaseBody().then(
                        tokenProvider.getToken().flatMap { newToken ->
                            val retryRequest = ClientRequest.from(request)
                                .headers { it.setBearerAuth(newToken) }
                                .build()
                            next.exchange(retryRequest)
                        }
                    )
                } else Mono.just(response)
            }
        }
}
```

**Rules**:
- Always release the body of the 401 response before retrying, or the connection will not be returned to the pool.
- Retry exactly once. If the retry also returns 401, propagate as-is.

---

## 6. Logging

### Logger declaration

Use SLF4J `LoggerFactory` directly — do not use `mu.KotlinLogging` (project convention):

```kotlin
private val log = LoggerFactory.getLogger(MyService::class.java)
```

### Structured log messages

Always use `{}` placeholders. Never use Kotlin string interpolation (`${}`) in log calls — it evaluates eagerly and cannot be suppressed at a lower log level:

```kotlin
// correct
log.info("keycloak merchant group synced merchantId={}", event.merchantId)

// wrong — interpolation pays allocation cost even when INFO is disabled
log.info("keycloak merchant group synced merchantId=${event.merchantId}")
```

Contextual values go inline as `key=value` pairs, most significant ID first:

```kotlin
log.warn("keycloak returned 401, retrying with fresh token method={} uri={}", request.method(), request.url())
log.error("keycloak branch group sync failed branchId={} merchantId={}", event.branchId, event.merchantId, e)
```

### Log levels

| Level | When to use |
|-------|-------------|
| `DEBUG` | Token lifecycle (fetch, cache hit, invalidation), individual HTTP calls inside a multi-step flow |
| `INFO` | Successful business operation at the service boundary (group created, name updated) |
| `WARN` | Recoverable condition: resource not found for an update, 401 triggering a token retry |
| `ERROR` | Unrecoverable failure propagated to the caller |

### Error logging — always pass the `Throwable`

SLF4J treats the last argument as a `Throwable` when it is one, appending the full stack trace to the log entry. Pass `e` directly; never pass `e.message`:

```kotlin
// correct — stack trace is captured
log.error("keycloak token fetch failed", e)
log.error("keycloak merchant group sync failed merchantId={}", event.merchantId, e)

// wrong — stack trace is lost; only the message string is logged
log.error("keycloak token fetch failed: {}", e.message)
```

### Placement in reactive chains

Log at the **service boundary** (the class that calls the HTTP interface), not inside the interface or the filter. Use `doOnSuccess` / `doOnError` on the terminal operation so each logical action produces exactly one log entry regardless of how many internal steps it takes:

```kotlin
fun syncMerchant(event: MerchantActivated): Mono<Void> =
    ensureRootGroup()
        .flatMap { rootId -> ensureMerchantGroup(rootId, event.merchantId, event.merchantName) }
        .doOnSuccess { log.info("keycloak merchant group synced merchantId={}", event.merchantId) }
        .doOnError { e -> log.error("keycloak merchant group sync failed merchantId={}", event.merchantId, e) }
        .then()
```

Token-level `DEBUG` events (fetch, invalidation) belong in the token provider, not in the service.

### Wire-level tracing (non-production only)

To see raw HTTP requests and responses from the underlying Netty client, set temporarily in `application.yml`:

```yaml
logging.level:
  reactor.netty.http.client: DEBUG
```

Do not commit this setting.

---

## 7. Idempotency

HTTP Interface services that back `@ApplicationModuleListener` handlers receive at-least-once delivery. Design operations to be safe when called more than once:

- **GET → create-if-absent, or update-if-present** (never blindly `POST`).
- Handle `409 Conflict` from `POST` by re-fetching the existing resource.
- Use exact-search parameters (`exact = true`) when the API supports them to avoid returning unintended matches.

---

## 8. Testing

### Unit tests — mock the interface

```kotlin
private val client = mock<KeycloakGroupClient>()
private val service = MerchantGroupSyncService(client)

@Test
fun `syncMerchant creates merchant group`() {
    whenever(client.searchGroups("merchants", true)).thenReturn(Mono.just(listOf(rootGroup)))
    whenever(client.getChildren(rootGroupId, "merchant_$merchantId", true)).thenReturn(Mono.just(emptyList()))
    whenever(client.createChildGroup(eq(rootGroupId), any())).thenReturn(createdResponse(merchantGroupId))

    service.syncMerchant(MerchantActivated(merchantId, "Acme GmbH")).block()

    verify(client).createChildGroup(eq(rootGroupId), any())
}
```

### Contract tests — `@Profile("contract-test")` guard

Under the `contract-test` Spring profile, `KeycloakWebClientConfig` is excluded (via `@Profile("!contract-test")`). Provide a mock bean instead:

```kotlin
@TestConfiguration
@Profile("contract-test")
class KeycloakMockConfig {
    @Bean fun keycloakGroupClient(): KeycloakGroupClient = mock()
}
```

This keeps contract tests runnable without any live Keycloak instance.
