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

Generated sources live under `build/generated/jooq/main` and must not be committed.
Production compilation must consume these generated sources; schema changes are not
complete until generation and compilation both pass.

The generator parses the authoritative create migrations required by current jOOQ query
services with jOOQ's PostgreSQL DDL parser. When a query starts using another table, add
that table's schema-defining migrations to the generator input in `build.gradle.kts`.
Runtime schema changes still belong exclusively under
`src/main/resources/db/changelog/`; do not create a duplicate hand-maintained schema.

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
