# Research: MCP Bulk Operations for Products and Categories

## Decision 1: No new Liquibase migrations required

**Decision**: Implement entirely on top of the existing schema.  
**Rationale**: `merchant_products.product_category_id` (UUID, nullable) and `merchant_products.product_code` (BIGINT, nullable) already exist. `merchant_product_categories` table is complete. Nothing new is needed.  
**Alternatives considered**: Adding a `description` column to `merchant_product_categories` — rejected; not requested and the spec excludes it.

---

## Decision 2: Partial-success via per-item service transactions

**Decision**: Loop over items in the MCP tool method and call the existing `MerchantProductService.create()` / `MerchantProductCategoryService.create()` per item, collecting successes and failures. No new `@Transactional` service method.  
**Rationale**: Both service methods are annotated `@Transactional` at class level. Calling them from the non-transactional `@McpTool` method means each item gets its own transaction. A `runCatching {}` per item collects errors without rolling back successful items.  
**Alternatives considered**: A dedicated `createBulk()` service method with `REQUIRES_NEW` savepoints — adds complexity without benefit since the existing per-call transaction boundary already achieves the right semantics.

---

## Decision 3: Duplicate detection strategy

### Categories

`MerchantProductCategoryService.create()` throws `IllegalArgumentException` when a name already exists. Catch this in the MCP tool and classify as `skipped` with `reason = "already exists"`.  
Within-batch duplicates (same name appears twice in the input list): track seen names in a `LinkedHashSet`; second occurrence is classified `skipped` before calling the service.

### Products

`MerchantProductService.create()` silently upserts when `productCode` matches an existing record — unsuitable for bulk where the caller expects explicit skipped/failed signals.  
Strategy: call `merchantProductService.findByNameIncludingInactive(merchantId, branchId, productName)` before `create()`. If non-null, classify as `skipped` with the existing `productId`. If null, proceed with `create()` wrapped in `runCatching`.  
Within-batch duplicates (same `productName` appears twice): track seen names in a `LinkedHashSet`; second occurrence is classified `skipped` immediately.

---

## Decision 4: Max batch size — 200 items, enforced at MCP tool boundary

**Decision**: Guard at the top of each bulk tool method: `require(items.size <= 200)`.  
**Rationale**: Rejects the entire call before touching the DB. 200 is sufficient for any realistic menu/catalog import and keeps response payloads manageable.  
**Alternatives considered**: No limit — too risky for memory and latency; configurable limit — over-engineering for this use case.

---

## Decision 5: `product_id_by_name_or_create` missing `categoryId` — fix in same PR

**Decision**: Add `categoryId: String? = null` parameter to `getOrCreateProductId` in `MerchantProductTools` and forward it to `CreateProductRequest`.  
**Rationale**: The entity column and request model already support it; the tool just never wired it through. The gap is a one-line fix.  
**Alternatives considered**: Separate PR — unnecessary overhead; the fix is trivial and directly related to this feature.

---

## Decision 6: Bulk response model placement

**Decision**: New types (`BulkProductCreateResult`, `BulkCategoryCreateResult`, `BulkSkippedItem`, `BulkFailedItem`) go in `merchant/api/` alongside `MerchantProductModels.kt` and `MerchantProductCategoryModels.kt`.  
**Rationale**: They are returned by MCP tools (which are module-private), but as stable public-surface response shapes they belong in `api/` per the DTO placement rules in `module-packaging-convention.md`.

---

## Decision 7: MCP parameter type for item lists

**Decision**: Define `BulkProductInput` and `BulkCategoryInput` as data classes in `merchant/api/`. Spring AI `@McpTool` serializes/deserializes them from JSON automatically.  
**Rationale**: Data-class parameters are idiomatic for structured inputs; they give the MCP schema a clear machine-readable shape. Passing a raw JSON string would require manual parsing.

---

## Decision 8: `getOrCreateProductId` image parameters — unchanged

The bulk create tool intentionally omits image upload (no `imageBase64`). This is consistent with the spec ("catalog import" scenario) and keeps the bulk payload size reasonable. Images can be added post-import via `merchant_product_update` using base64 image bytes plus an explicit content type.
