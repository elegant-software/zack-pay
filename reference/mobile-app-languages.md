# Mobile app language contract (#228; prerequisite for mobile #67)

## Audit and scope

Before this change, merchant create/update/detail already supported `originalMenuLanguage` and
`supportedMenuLanguages`, backed by the platform menu-language catalogue. Product list/detail and
category list already selected translations with `language` or `Accept-Language` and fell back per
item to original text. Orders already snapshotted product text and prices. These behaviors are preserved.
The missing capabilities were authenticated user settings and inheritance of original language in branch responses.

## Authenticated user settings

All requests require a resource-server-validated bearer JWT. Identity comes exclusively from its `sub`.
There is no account ID path/query/body selector; even administrators operate on their own settings.
No merchant role or branch membership is required: any authenticated account can keep both preferences.

- `GET /v1/user-settings`: returns `shopperAppLanguage` and `merchantAppLanguage`.
- `PATCH /v1/user-settings`: accepts either or both of those fields; returns the resulting settings.

Saved fields use the `AppLanguage` domain/OpenAPI enum: JSON `"en"` or `"de"` only.
Each independently returns `null` until explicitly saved. An unset preference is distinct from a saved
English choice, so apps can follow device or merchant defaults without persisting an inferred choice.
At sign-in, apps can adopt a signed-out explicit choice only when that app preference is unset. Example:

```json
{"shopperAppLanguage":"de"}
```

This changes only the shopper preference. Omitted or null fields are unchanged; `{}` is a no-op.
Separate preference rows and atomic PostgreSQL upserts preserve the other app preference and unrelated
settings, including concurrent updates to different preferences. Preferences persist server-side across
sessions and devices for that account, and are erased on account deletion. Missing/invalid authentication
returns 401; invalid language values return 400. Settings do not automatically select catalog text: clients
send the desired language on catalog reads. Catalog responses never write preferences.

## Merchant original language

`GET /v1/merchants/{merchantId}` continues to expose `originalMenuLanguage` and
`supportedMenuLanguages`. Merchant create/update continue to configure these fields; branches cannot
override them. `BranchResponse.originalMenuLanguage` is now included in branch create, list, detail,
update, image-update and bulk responses, including:

- `GET /v1/merchants/{merchantId}/branches`
- `GET /v1/merchants/{merchantId}/branches/{branchId}`

The same `originalMenuLanguage` field is also inherited in discovery branch objects:
`GET /v1/merchants/nearby?lat=...&lng=...[&radiusMeters=500]` returns it in
`merchants[].activeBranches[]`; `POST /v1/proximity` returns it in `merchant.branches[]`
when an enter event produces merchant context. Both discovery endpoints require authentication.

The value is read from the parent merchant, so all branches agree and reflect later merchant changes.
It is null when the merchant has no configured original language; clients must not assume English.
Menu languages remain catalogue-managed BCP 47 strings, not the closed two-value app-language enum:
merchants can author content in languages beyond English and German.

Merchant detail, branch detail, and product list/detail reads are public storefront contracts. Branch list
and category list require authentication under the existing security configuration. Existing write access
rules are unchanged by this feature.

## Product and category language selection

Relevant existing endpoints:

- `GET /v1/merchants/{merchantId}/products?branchId={branchId}[&categoryId={categoryId}]`
- `GET /v1/merchants/{merchantId}/products/{productId}[?branchId={branchId}]`
- `GET /v1/merchants/{merchantId}/product-categories`

All accept `Accept-Language` and optional `language`. For mobile shoppers, send the saved
`shopperAppLanguage` as `Accept-Language: de` or `Accept-Language: en`. With International Menu enabled,
weighted supported header languages are selected using their primary subtag (`de-AT` matches `de`).
If none match, the merchant has only its original language, or International Menu is disabled, original
text is served. This keeps single-language merchants usable for either app language.

The existing explicit `?language=...` parameter takes precedence and allows a merchant's configured
original or supported language regardless of International Menu. A language the merchant does not offer
returns 400 on this strict parameter. Use the header for shopper requests that should always fall back.

Each product remains in the result when its selected translation is absent. The product returns its
original `name` and `description`; its `language` identifies the original language. When a translation
exists, `name`, `description`, and `language` come from that translation (a null translated description
remains null). Category names independently fall back to the original category name. Category list items
also expose their returned `language`. Existing translations marked stale remain readable.

The returned `language` describes content, not app UI preference. Never replace either saved app language
with a product's fallback language. Original-language metadata can be null for older unconfigured merchants.

## Commercial invariants and checks

Preferences and translated catalog reads do not write products, orders, payments, invoices, or prices.
Orders retain their snapshotted product names/descriptions, quantities, unit prices, line totals and price
overrides. Language selection cannot change currency or monetary amounts; order reads continue to serve
historical text rather than retranslate it from the current catalog.

Regression coverage includes settings defaults/persistence/isolation, independent updates, anonymous access,
unsupported enum rejection, branch inheritance, existing translation selection/fallback and order snapshot
and price preservation. `UserSettingsPersistenceTest` additionally executes the migration and repository
upsert SQL against PostgreSQL 16 and reconnects to verify persistence; it requires Docker and skips when
Docker is unavailable. Run `./gradlew check` for unit and HTTP contract tests.
