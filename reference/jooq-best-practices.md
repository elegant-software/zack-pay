# jOOQ Best Practices

Authoritative reference for type-safe SQL query conventions in this project.

---

## 1. Use Generated Schema Types

**Rule:** Application queries must use jOOQ-generated tables, fields, keys, and records.

```kotlin
import com.elegant.software.blitzpay.jooq.tables.references.ORDER_ORDERS
import com.elegant.software.blitzpay.jooq.tables.references.PAYMENT_SETTLEMENTS

val settlements = PAYMENT_SETTLEMENTS
val orders = ORDER_ORDERS

dsl.select(
    settlements.CURRENCY,
    settlements.PAYMENT_METHOD,
    settlements.GROSS_AMOUNT_MINOR.sum(),
)
    .from(settlements)
    .join(orders).on(settlements.ORDER_ID_FK.eq(orders.ID))
```

Do not declare schema objects with plain SQL strings:

```kotlin
// Wrong: column and table names fail only at runtime.
val settlements = table("blitzpay.payment_settlements")
val currency = field("blitzpay.payment_settlements.currency", String::class.java)
```

Generated types provide compile-time column names, database-derived Kotlin types,
refactor safety, and reliable field identity when reading records.

Plain SQL fields are permitted only for database expressions that jOOQ cannot model
through its DSL. Such usage must be narrowly scoped and explained with a comment.

## 2. Reuse Selected Expressions

Store calculated or aliased fields once and use the same field object in both `select`
and record access.

```kotlin
val totalGross = PAYMENT_SETTLEMENTS.GROSS_AMOUNT_MINOR.sum().`as`("total_gross")

val row = dsl
    .select(totalGross)
    .from(PAYMENT_SETTLEMENTS)
    .fetchOne()

val gross = row?.get(totalGross) ?: BigDecimal.ZERO
```

Do not retrieve selected values by string name:

```kotlin
// Wrong: qualification and alias resolution can differ from the selected field identity.
row.get("total_gross", Long::class.java)
```

## 3. Aliases

Alias generated tables when a query joins the same table more than once or when an
alias materially improves readability.

```kotlin
val settlement = PAYMENT_SETTLEMENTS.`as`("settlement")
val order = ORDER_ORDERS.`as`("merchant_order")
```

Use the fields from the aliased table instance. Do not mix fields from the original
generated table with an aliased table in the same query.

## 4. Schema Generation

Liquibase remains the schema source of truth. Regenerate jOOQ classes after every
Liquibase schema change:

```bash
./gradlew generateJooq
```

`generateJooq` starts a `postgres:16-alpine` Testcontainer, applies the full Liquibase
changelog to it, and runs jOOQ codegen against the resulting `blitzpay` schema
(`src/jooqCodegen/kotlin/.../JooqCodegen.kt`).

Generated sources live under `src/generated/jooq` and **must be committed**, so normal
compilation (including the Docker image build) needs no database. The CI job
`jooq-codegen-check` regenerates them and fails if the committed copy is stale.
Schema changes are not complete until generation, compilation, and the commit of the
regenerated sources are done.

Only tables listed in `INCLUDED_TABLES` in `JooqCodegen.kt` are generated. When a query
starts using another table, add it there and regenerate. Runtime schema changes still
belong exclusively under `src/main/resources/db/changelog/`; do not create a duplicate
hand-maintained schema.

### Docker for code generation

`generateJooq` needs a Docker daemon reachable by Testcontainers. Testcontainers does
**not** support `ssh://` Docker contexts, so the active `docker context` is irrelevant;
it resolves Docker from `DOCKER_*` environment variables, then
`~/.testcontainers.properties`, then the local socket.

**Remote daemon (e.g. `myserver`, plain TCP on port 2375, no TLS):**

```properties
# ~/.testcontainers.properties
docker.host=tcp://myserver:2375
testcontainers.host.override=myserver
testcontainers.reuse.enable=true
ryuk.disabled=true
```

`testcontainers.host.override` makes the mapped container ports reachable at the remote
host instead of `localhost`. Or pass the host for a single run:

```bash
unset DOCKER_TLS_VERIFY DOCKER_CERT_PATH
DOCKER_HOST=tcp://myserver:2375 ./gradlew generateJooq
```

**Local Docker Desktop:** start Docker Desktop and remove `docker.host` and
`testcontainers.host.override` from `~/.testcontainers.properties`.

**Troubleshooting:**

| Error | Cause | Fix |
|---|---|---|
| `Could not find a valid Docker environment` … `/var/run/docker.sock` | No local daemon running and no `DOCKER_HOST` / `docker.host` set | Start Docker Desktop or point at the remote daemon (above) |
| `Enabled TLS verification (DOCKER_TLS_VERIFY=1) but certificate path … doesn't exist` | Stale `DOCKER_TLS_VERIFY` / `DOCKER_CERT_PATH` in the shell (e.g. from `docker-machine env`) | `unset DOCKER_TLS_VERIFY DOCKER_CERT_PATH` — the 2375 daemon is plain TCP |
| Container starts but JDBC connection times out | `testcontainers.host.override` doesn't match where the daemon runs | Set it to the remote host, or remove it for local Docker |

## 5. Query Ownership

- Keep jOOQ queries in the module that owns the queried tables.
- Do not bypass Spring Modulith boundaries by querying another module's tables.
- Return domain-specific DTOs from query services rather than exposing jOOQ records.
- Keep HTTP controllers unaware of `DSLContext`, generated tables, and records.

## 6. Testing

- Add a focused test for field mapping, aggregation, filtering, and pagination changes.
- Prefer database-backed tests for PostgreSQL-specific behavior.
- For mapping-only tests, construct records with the exact selected generated fields and
  aliased expressions.
- Run `./gradlew check` before considering a jOOQ change complete.
