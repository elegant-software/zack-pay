# Feature Specification: merchant-location-product-api

**Feature Branch**: `001-merchant-onboarding`
**Created**: 2026-04-21
**Status**: Draft
**Input**: "Expose merchant location and product create/update API on the MCP server so other LLM/AI agents can create and update merchant locations and products. MCP ingestion may create placeholder branches/products, but newly created records must remain inactive until reviewed."

## User Scenarios & Testing (mandatory)

### User Story 1 - Create Merchant Location (Priority: P1)
An automated agent or admin needs to register a physical or operational location for an existing MerchantBranch so customers and downstream systems can reference it.

Independent Test: Call the MCP API to create a location with branchId; verify the location is persisted and returned with id, createdAt, and updatedAt.

Acceptance Scenarios:
1. Given an existing branch, when the agent calls POST /v1/branches/{branchId}/locations with valid payload, then a 201 is returned and location is associated to the branch.
2. Given an unknown branch, when the same call is made, then a 404 is returned.

---

### User Story 2 - Create Product for a Location (Priority: P1)
An automated agent needs to create a product tied to a specific MerchantLocation so payments and listings can reference location-scoped products.

Independent Test: Call POST /v1/branches/{branchId}/locations/{locationId}/products; verify 201 and product fields persisted.

Acceptance Scenarios:
1. Given an existing location, when creating a product with required fields (name, priceMinor, currency), then a 201 and product id are returned.
2. Given an unknown location, a 404 is returned.

---

### User Story 3 - Update Location and Product (Priority: P2)
Agents must be able to update mutable fields (address, geolocation, product price/availability) without changing branch ownership.

Independent Test: PATCH endpoints update fields and return 200 with updated resource and updatedAt changed.

Acceptance Scenarios:
1. Valid updates return 200 and reflect new values.
2. Concurrent updates are handled idempotently (last-writer-wins by updatedAt) and return appropriate status.

---

### User Story 4 - Ingest Catalog Images from Agent Sources (Priority: P2)
Agents must be able to create or update product records from menu photos by supplying a local image path or base64 image bytes, optionally with crop coordinates that isolate the product image before upload.

Independent Test: Call the MCP product tool with valid base64-encoded JPEG/PNG image bytes, content type, and crop rectangle; verify the product image is uploaded to object storage, the product stores an object key, and the product remains inactive if newly created.

Acceptance Scenarios:
1. Given base64-encoded image bytes and a supported content type, when the agent creates or updates a product through MCP, then the image is uploaded through the standard private object-storage path.
2. Given valid crop coordinates, when the agent submits the image through MCP, then only the cropped image is stored.
3. Given missing base64 image bytes, invalid base64, unsupported content type, oversized image, or invalid crop rectangle, then the MCP call fails without leaving a partial product update.

## Edge Cases

- Creation requests for locations/products with duplicate external IDs should be idempotent.
- Partial updates with invalid data must return 400 without mutating persisted resource.
- Product creation when the branch exists but location is inactive should return 409 or configurable policy.
- MCP-created branches/products must not become buyer-visible before review; they are created with `active = false`.
- MCP lookup/update must find inactive records so repeated ingestion updates the same draft record instead of creating duplicates.

## Requirements (mandatory)

### Functional Requirements

- FR-001: MCP MUST expose create and update APIs for MerchantLocation tied to an existing MerchantBranch.
- FR-002: MCP MUST expose create and update APIs for Product scoped to a merchant branch/location context.
- FR-003: API MUST validate that branchId exists and belongs to the requesting tenant when a branch id is supplied. Authentication MUST be via tenant-scoped JWT including a branch ownership claim (e.g., branch_id or tenant_id). Requests lacking proper tenant/branch claim MUST be rejected with 403.
- FR-004: API MUST return standard HTTP status codes: 201 on create, 200 on update, 400 on validation errors, 404 for missing parent resources, 409 for business conflicts, 412 for precondition failures (optimistic locking). Updates SHOULD accept an If-Match (ETag) header or a version field and return 412 Precondition Failed when the client's expected version does not match the current resource.
- FR-005: Persistent fields: id, branchId, locationId (for product), createdAt, updatedAt, active flag.
- FR-006: API MUST support idempotent create. Support both an Idempotency-Key header (server-side deduplication by header) and an optional client-supplied UUID in the request body. The server should treat requests with the same Idempotency-Key or client-supplied id as the same operation and return the original resource on retries. Return 409 only for business conflicts that are not idempotent retries.
- FR-007: Auditing: user/agent identity and request metadata recorded for create/update actions.
- FR-008: Updates MUST support partial updates via PATCH for MerchantLocation and Product; PUT (full replace) optional.
- FR-009: MCP branch and product creation MUST default new records to `active = false`; lookup/update tools MUST search active and inactive records.
- FR-010: MCP product image ingestion MUST accept `imageBase64` plus `imageContentType`, and MAY accept `cropX`, `cropY`, `cropWidth`, and `cropHeight` for server-side JPEG/PNG cropping before upload. Tool callers SHOULD provide clear, high-quality images directly related to the product; product images MUST be JPEG, PNG, or WebP and 5 MB or smaller.
- FR-011: MCP image ingestion MUST use the same private object-storage, object-key persistence, MIME validation, size validation, and signed retrieval behavior as the REST product image API.
### Key Entities

- MerchantBranch (existing)
- MerchantLocation: id, branchId, name, address fields, geolocation (lat/long), googlePlaceId, active, createdAt, updatedAt
- Product: id, branchId, locationId if applicable, name, description, priceMinor/decimal price, currency or merchant-inherited currency, active, image object key, createdAt, updatedAt

## Success Criteria (mandatory)

- SC-001: Agents can create a MerchantLocation and Product and receive a persistent id within one request cycle (end-to-end success measured by 201 + read-back verification).
- SC-002: 95% of valid create/update requests complete within acceptable latency for agent workflows (user-facing "near-instant").
- SC-003: All created/updated resources include audit metadata (creator id, timestamp).
- SC-004: MCP-created branches/products are inactive by default and can be reviewed before becoming visible in buyer-facing views.

## Assumptions

- Agents normally supply a valid existing branchId; helper tools may create missing placeholder branches by name, but those branches remain inactive until reviewed.
- Authentication/authorization is provided by existing MCP infrastructure; endpoints will enforce tenant/branch ownership rules.
- Prices are represented as minor units (integer) and currency is ISO-4217 code.
- Backwards compatibility: existing merchant/branch data model remains unchanged.


## Clarifications

### Session 2026-04-21
- Q: Update semantics for MerchantLocation/Product APIs → A: PATCH (partial updates) (recommended: easier for agents and avoids sending full resource)
- Q: Domain canonical name for routes → A: Support both (C) - alias /v1/merchants and /v1/branches (route aliasing)
- Q: Idempotency strategy for create endpoints → A: Support both header and optional client id (D) - flexible, prefer Idempotency-Key when available
- Q: Authorization/tenant enforcement → A: Require tenant-scoped JWT with branch claim (A) - enforces tenant isolation and least privilege
- Q: Conflict resolution for concurrent updates → A: Optimistic locking (B) - use If-Match/ETag or version precondition, return 412 on mismatch

### Session 2026-04-23
- Q: Can MCP tools create branches/products during catalog ingestion? → A: Yes, helper tools can create missing records by name, but created records default to inactive.
- Q: How should MCP product image upload work? → A: Accept base64 bytes plus an explicit content type, with optional crop coordinates, then upload through the same object-storage path as the REST product image API.
