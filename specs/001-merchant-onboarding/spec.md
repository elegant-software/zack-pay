# Feature Specification: Merchant Onboarding

**Feature Branch**: `[001-merchant-onboarding]`  
**Created**: 2026-03-22  
**Status**: Draft  
**Input**: User description: "merchant-onboarding"

## Clarifications

### Session 2026-03-23

- Q: Should this feature scope end at approval, include activation, or also include ongoing monitoring? → A: Scope includes activation and ongoing monitoring.
- Q: Should EU AML/KYB and GDPR obligations be explicit requirements in this feature? → A: EU AML/KYB and GDPR obligations are explicit requirements in this feature.

### Session 2026-04-19 (product catalog)

- Q: Where should product images be stored? → A: Object storage (S3-compatible); product record stores the image reference, later clarified as a stable image ID/object key. (FR-032 added)
- Q: Should products support a deactivated/archived state, or is simple create/update/delete sufficient? → A: Soft delete — products have an `active` flag; inactive products are hidden from buyers but retained. (FR-033 added)
- Q: Who can create and manage products for a merchant? → A: Both — merchants manage their own catalog via self-service API; internal admins can override. (FR-029, FR-030 added)
- Q: Is product unit price always in the merchant's registered currency, or can each product carry its own currency? → A: Product price inherits the merchant's registered currency — no per-product currency field. (FR-031 updated)
- Q: Should zero-price products be allowed, and how is unit price expressed? → A: Zero-price allowed — free/sample products are a valid use case; unit price is decimal ≥ 0. (FR-034 added)
- Q: Multi-tenancy practices must be followed for product tables — how should tenant isolation be enforced? → A: Both — application-level filtering (`merchant_id` scoping) as primary path; PostgreSQL Row Level Security as safety net. (FR-035, FR-036 added)

### Session 2026-04-19 (merchant location)

- Q: Are latitude and longitude required fields during merchant registration? → A: Optional — coordinates and Place ID can be provided or enriched after registration. (FR-037 added)
- Q: Where should latitude, longitude, and Google Place ID be stored? → A: Separate `MerchantLocation` entity — new table with FK to `merchant_applications`. (FR-038 added)
- Q: Should location be updatable after initial registration? → A: Yes, via a dedicated `PUT /v1/merchants/{id}/location` endpoint. (FR-039 added)
- Q: Should Google Maps Place ID be validated against Maps API on save? → A: Store as-is on request; a required background job validates and enriches it asynchronously. (FR-040 added)

### Session 2026-04-21

- Q: Should product image records store direct object storage URLs or stable object identifiers? → A: Store a stable image ID/object key; API responses return retrieval URLs when requested.
- Q: Should product image upload use a dedicated upload endpoint or be included with product create/update? → A: Product create/update accepts multipart data containing product fields and the image file together.
- Q: Should product images be public objects or private objects behind API-generated retrieval URLs? → A: Private objects; API returns short-lived signed retrieval URLs.
- Q: Which product image formats and size limit should the API accept? → A: JPEG, PNG, and WebP images up to 5 MB.
- Q: Should merchant branches store customer-facing address and geo information? → A: Yes — branch records store operational address, geolocation, geofence, Google Place ID, and enrichment metadata.
- Q: Should products include a description, and what format/limit should apply? → A: Optional rich-text/Markdown description, max 2,000 characters.

### Session 2026-04-23 (MCP catalog ingestion)

- Q: Can MCP tools upload and crop product images? → A: Yes — MCP product tools accept `imageBase64` plus `imageContentType`, optional crop coordinates, and upload the resulting JPEG/PNG/WebP bytes through the same private object-storage path as REST product image updates. Product images should be clear, high-quality, directly related to the product, and 5 MB or smaller.
- Q: Should branches and products created through MCP ingestion be immediately buyer-visible? → A: No — MCP-created branches and products default to inactive so imported catalog data can be reviewed before publication. Existing branches/products found by name may still be updated by MCP regardless of active state.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit Merchant Application (Priority: P1)

A prospective merchant can start onboarding, provide required business, ownership, and contact details, review their information, and submit an application that satisfies EU compliance intake requirements to join BlitzPay.

**Why this priority**: Without a complete application flow, merchants cannot begin the onboarding journey and the business cannot acquire new merchants.

**Independent Test**: Can be fully tested by completing a new merchant application from start to submission and verifying that the application is stored with a submitted status and confirmation is shown to the applicant.

**Acceptance Scenarios**:

1. **Given** a prospective merchant has not started onboarding, **When** they enter all required business and primary contact information and submit the application, **Then** the system records the application and confirms successful submission.
2. **Given** a prospective merchant leaves required fields incomplete or enters invalid values, **When** they attempt to continue or submit, **Then** the system highlights the issues and prevents submission until they are corrected.
3. **Given** a prospective merchant has entered onboarding information, **When** they review the application before submitting, **Then** they can verify and correct the details before final submission.
4. **Given** compliance-related information or supporting materials are required, **When** the merchant attempts to submit without them, **Then** the system prevents submission and identifies what is still required.

---

### User Story 2 - Track Onboarding Status (Priority: P2)

A merchant who has submitted an application can view the current onboarding status, understand whether action is required, and see the next step needed to become active.

**Why this priority**: Status visibility reduces uncertainty, lowers support burden, and keeps merchants moving through the onboarding funnel.

**Independent Test**: Can be fully tested by submitting an application, moving it through multiple status states, and verifying the merchant sees the latest status, outstanding actions, and final outcome.

**Acceptance Scenarios**:

1. **Given** a merchant has submitted an application, **When** they return to the onboarding experience, **Then** they can see the current status of their application.
2. **Given** additional information is required from the merchant, **When** the application status changes to action required, **Then** the merchant sees what is missing and can resubmit the requested information.
3. **Given** the application is approved or rejected, **When** the merchant views their status, **Then** they see the outcome and any relevant next-step guidance.

---

### User Story 3 - Review and Approve Applications (Priority: P3)

An internal operations reviewer can evaluate submitted merchant applications, request corrections when information is incomplete, approve or reject applications with a clear decision trail, and ensure approved merchants move into setup, activation, and ongoing monitoring.

**Why this priority**: Merchant onboarding only creates business value if internal teams can process applications, activate eligible merchants, and place them into ongoing compliance oversight.

**Independent Test**: Can be fully tested by reviewing a submitted application, recording a decision or request for changes, activating an approved merchant, and verifying the merchant enters ongoing monitoring with the correct visible status updates.

**Acceptance Scenarios**:

1. **Given** an application has been submitted, **When** an operations reviewer opens it, **Then** they can see all submitted details and supporting materials needed to make a decision.
2. **Given** an application is incomplete or inconsistent, **When** the reviewer requests additional information, **Then** the application status changes accordingly and the merchant is informed that action is required.
3. **Given** an application meets onboarding criteria, **When** the reviewer approves it, **Then** the application proceeds through setup and activation and the merchant enters ongoing monitoring.
4. **Given** an application raises compliance concerns during verification, screening, or risk review, **When** an authorized reviewer evaluates it, **Then** the reviewer can record the compliance outcome and advance, hold, or reject the merchant accordingly.

### Edge Cases

- What happens when a merchant starts onboarding but leaves before submitting? The system preserves progress as a draft so the merchant can resume later.
- What happens when the merchant submits duplicate business registrations? The system detects likely duplicates and prevents multiple active applications for the same business without explicit review.
- How does the system handle missing or unreadable supporting materials? The system blocks submission if required materials are missing and flags unreadable materials for correction if discovered during review.
- How does the system handle a merchant whose application is approved after previously being sent back for corrections? The full history of review decisions and merchant updates remains visible.
- What happens when a merchant attempts to read or modify a product belonging to a different merchant? The application MUST reject the request with a 403/404 before reaching the database; RLS MUST additionally block the query at the database layer as a secondary defence.
- What happens when a product image upload to object storage fails? The system MUST reject the product create/update request and return an error; no partial product record is persisted.
- What happens when a product's stored image object key no longer resolves in object storage? The system MUST return a null image field rather than a broken retrieval URL or error.
- What happens when a product create/update request includes an unsupported image type or an image larger than 5 MB? The system MUST reject the request with HTTP 400 and a descriptive validation error before writing the product record or object.
- What happens when a product create/update request includes a description longer than 2,000 characters? The system MUST reject the request with HTTP 400 and a descriptive validation error.
- What happens when an MCP catalog-ingestion call creates a new branch or product? The created branch or product MUST be persisted with `active = false` by default so it is not buyer-visible until reviewed and activated.
- What happens when an MCP catalog-ingestion call references a branch or product that already exists but is inactive? The MCP tool MUST be able to find and update the existing inactive record by name/merchant/branch rather than creating a duplicate active record.
- What happens when an MCP product image upload supplies invalid base64, unsupported content type, oversized image bytes, invalid crop rectangle, or object-storage upload failure? The MCP call MUST fail with a clear validation/storage error and MUST NOT leave a partial product image update.
- What happens when a `PUT /v1/merchants/{id}/location` request supplies an out-of-range latitude (< −90 or > 90) or longitude (< −180 or > 180)? The system MUST reject the request with HTTP 400 and a descriptive validation error; no location record is created or modified.
- What happens when the Google Maps API is unavailable during Place ID enrichment? The system MUST keep the stored Place ID, record the enrichment failure for retry/observability, and leave the merchant location API usable.
- What happens when a merchant branch has no address or geo information? The system MUST allow the branch to exist but omit it from location-based discovery until address and coordinates are set.
- What happens when an active merchant later triggers monitoring concerns? The system records the monitoring event, updates the merchant's oversight status, and supports follow-up review actions without losing the original onboarding history.
- What happens when a merchant requests access to or correction of personal data during onboarding? The system supports those requests in line with applicable privacy obligations without breaking the compliance record.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a prospective merchant to start a new onboarding application.
- **FR-002**: The system MUST collect required business details, including legal business name, business type, registration details, operating country, and primary business address.
- **FR-003**: The system MUST collect required primary contact details for the merchant application.
- **FR-004**: The system MUST collect required ownership and control details needed to identify the merchant's key responsible persons and beneficial owners.
- **FR-005**: The system MUST clearly distinguish required and optional information throughout the onboarding flow.
- **FR-006**: The system MUST validate submitted information for completeness, format, and logical consistency before allowing final submission.
- **FR-007**: The system MUST allow a merchant to save an in-progress application and resume it later before submission.
- **FR-008**: The system MUST allow a merchant to review and edit onboarding information before final submission.
- **FR-009**: The system MUST require explicit confirmation from the merchant before final submission of the application.
- **FR-010**: The system MUST assign each submitted application a unique reference that can be used by the merchant and internal teams.
- **FR-011**: The system MUST record and display the current onboarding status for each application.
- **FR-012**: The system MUST support review stages for business verification, screening, risk assessment, decisioning, setup, activation, and ongoing monitoring.
- **FR-013**: The system MUST support at least the following lifecycle states: draft, submitted, verification, screening, risk review, decision pending, setup, active, and monitoring.
- **FR-014**: The system MUST allow internal reviewers to view the full contents of a submitted application, including all compliance-related information and supporting materials.
- **FR-015**: The system MUST allow internal reviewers to request additional information or corrections from the merchant.
- **FR-016**: The system MUST allow merchants to respond to requested corrections and resubmit their application.
- **FR-017**: The system MUST allow internal reviewers to approve or reject an application and capture the reason for the decision.
- **FR-018**: The system MUST notify the merchant when the application is submitted, when action is required, and when a final decision is made.
- **FR-019**: The system MUST maintain an auditable history of merchant submissions, status changes, reviewer decisions, and merchant resubmissions.
- **FR-020**: The system MUST prevent more than one active onboarding application for the same merchant business unless an internal reviewer explicitly permits an exception.
- **FR-021**: The system MUST protect submitted merchant information so that only the applicant and authorized internal reviewers can access it.
- **FR-022**: The system MUST progress an approved merchant through setup and activation steps before the merchant is marked active.
- **FR-023**: The system MUST create and maintain a monitoring record for each active merchant so that post-activation oversight events can be tracked.
- **FR-024**: The system MUST allow authorized internal users to record monitoring outcomes and any resulting follow-up actions against an active merchant.
- **FR-025**: The system MUST process merchant onboarding in line with EU anti-money-laundering and know-your-business obligations, including business verification, beneficial ownership identification, sanctions screening, and risk-based review.
- **FR-026**: The system MUST process merchant personal data in line with GDPR principles, including data minimization, purpose limitation, controlled access, and support for applicable data subject rights.
- **FR-027**: The system MUST retain onboarding and compliance records for the applicable legally required retention period and prevent premature deletion of required records.
- **FR-028**: The system MUST keep merchant onboarding and compliance records within approved regional storage boundaries for this feature's target market.
- **FR-029**: The system MUST allow an authenticated merchant to create, update, and deactivate products in their own catalog via a self-service API.
- **FR-030**: The system MUST allow authorized internal admin users to create, update, and deactivate products on behalf of any merchant.
- **FR-031**: Each product MUST have a name and a unit price expressed as a decimal number (≥ 0) in the merchant's registered currency; zero-price products are permitted to support free or sample offerings.
- **FR-046**: Each product MAY have an optional rich-text/Markdown description up to 2,000 characters; descriptions exceeding this limit MUST be rejected with a structured validation error.
- **FR-032**: Each product MAY have an optional image; images MUST be uploaded as private objects to an object storage service (S3-compatible), and the product record MUST store only a stable image ID/object key while API responses return a short-lived signed retrieval URL when requested.
- **FR-041**: Product create and update endpoints MUST accept multipart requests containing product fields and an optional image file; when an image file is present, the system MUST upload it to object storage as part of the product write operation.
- **FR-042**: Product image retrieval URLs MUST be generated by the API as short-lived signed URLs and MUST NOT require object storage buckets to allow public-read access.
- **FR-043**: Product image uploads MUST accept only JPEG, PNG, and WebP files up to 5 MB; unsupported MIME types or oversized files MUST be rejected with a structured validation error before product or object storage changes are persisted.
- **FR-033**: Products MUST support soft delete via an `active` flag; inactive products MUST be hidden from buyer-facing views but MUST be retained in the database for record-keeping.
- **FR-047**: MCP product ingestion tools MUST support optional product image upload using base64-encoded image bytes plus an explicit content type; when crop coordinates are supplied, the tool MUST crop the image before applying the same content-type, size, object-key storage, and signed retrieval URL behavior used by the REST product image flow. Tool callers SHOULD provide clear, high-quality images directly related to the product.
- **FR-048**: Branches and products created by MCP ingestion tools MUST default to `active = false`; MCP lookup/update tools MUST search existing inactive records as well as active records to support review-before-publish workflows without duplicate records.
- **FR-034**: Product unit price MUST NOT carry its own currency field; it is implicitly denominated in the owning merchant's registered currency.
- **FR-035**: All product-related database tables MUST include `merchant_id` as a non-nullable tenant discriminator column and MUST enforce a foreign key relationship to the merchant record.
- **FR-036**: All product queries MUST be scoped to the authenticated merchant's `merchant_id` at the repository/service layer (application-level filtering as primary isolation); PostgreSQL Row Level Security policies MUST additionally be applied on product tables as a safety net to prevent cross-tenant data leakage at the database layer.
- **FR-037**: A merchant MAY optionally provide geographic coordinates (latitude and longitude) and a Google Maps Place ID; none of these fields are required at registration time.
- **FR-038**: Latitude, longitude, and Google Maps Place ID MUST be stored in a separate `MerchantLocation` entity with a foreign key to `merchant_applications`; latitude and longitude MUST use `DECIMAL(9,6)` precision; Place ID MUST be stored as a nullable `VARCHAR`.
- **FR-039**: An authenticated merchant or internal admin MUST be able to create or replace the location record via `PUT /v1/merchants/{merchantId}/location`; the endpoint MUST also support removing location data by accepting null values.
- **FR-040**: The Google Maps Place ID MUST be accepted and stored as-is without real-time validation during the request; a background job MUST asynchronously validate and enrich stored Place IDs via the Google Maps API for address and reviews resolution.
- **FR-044**: Merchant branches MAY store a customer-facing operational address, latitude, longitude, geofence radius, Google Place ID, and Google Maps enrichment metadata; this branch location data MUST be separate from the merchant's legal registration address.
- **FR-045**: Location-based discovery MUST use branch-level address and geo information when available; merchant-level location MAY be used only as a fallback for merchants without branches.

### Key Entities *(include if feature involves data)*

- **Merchant Application**: A merchant’s onboarding record containing business information, contact details, submission state, status history, and final decision.
- **Business Profile**: The legal and operational identity of the merchant business, including registration, address, and classification details.
- **Person**: An individual associated with the merchant, such as a beneficial owner, director, or other responsible party whose identity or role must be assessed during onboarding.
- **Primary Contact**: The person responsible for submitting and managing the onboarding application on behalf of the merchant.
- **Review Decision**: A recorded internal action on an application, including status change, reviewer rationale, and timestamp.
- **Supporting Material**: Documents or evidence submitted by the merchant to support verification and approval.
- **Risk Assessment**: The record of compliance and business risk evaluation used to support onboarding decisions and downstream monitoring.
- **Monitoring Record**: The post-activation oversight record for an active merchant, including monitoring status, review triggers, outcomes, and follow-up actions.
- **Product**: A catalog item offered by a merchant, carrying a `merchant_id` tenant discriminator, a name, optional rich-text/Markdown description, unit price (decimal ≥ 0 in the merchant's registered currency), an `active` flag for soft delete, and an optional image ID/object key referencing object storage. Product create/update accepts multipart product fields with an optional image file; MCP ingestion can also supply base64 image bytes with optional crop coordinates. All queries are tenant-scoped by `merchant_id` at both application and database (RLS) layers.
- **MerchantLocation**: An optional location value linked to a merchant, holding latitude, longitude, geofence radius, and an optional `googlePlaceId`. Managed via `PUT /v1/merchants/{merchantId}/location`. Place ID validation and enrichment for address/reviews data is performed asynchronously by a background job.
- **MerchantBranch**: A customer-facing branch under a merchant. It carries branch name, optional operational address fields, optional branch location/geofence fields, optional Google Place ID, an `active` flag, and enrichment metadata used for branch discovery and customer-facing maps. MCP-created branches default to inactive until reviewed.

## Assumptions

- Merchant onboarding is intended for business customers rather than individual consumers.
- A standard review process is required before a merchant becomes active.
- Supporting materials are part of the onboarding process when needed to verify business legitimacy.
- Merchants are allowed to correct and resubmit applications instead of being forced to start over after a review request.
- Merchant notifications can be delivered through the platform’s existing communication channels.
- This feature includes post-activation monitoring at a high level, but detailed monitoring operations may still be decomposed further during planning.
- This feature targets an EU compliance context, so AML/KYB and GDPR obligations are part of the core scope rather than optional add-ons.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of prospective merchants who begin onboarding can submit a complete application without contacting support.
- **SC-002**: A merchant can complete and submit a standard onboarding application in 10 minutes or less under normal conditions.
- **SC-003**: At least 95% of submitted applications include all required information on first submission.
- **SC-004**: Internal reviewers can reach an approve, reject, or action-required decision for 90% of complete applications within 2 business days.
- **SC-005**: At least 85% of merchants with an action-required status successfully resubmit corrected information on their first follow-up attempt.
- **SC-006**: Support requests about onboarding status decrease by 40% after merchants gain self-service status tracking.
- **SC-007**: 100% of approved merchants are moved into active monitoring without manual off-system tracking.
- **SC-008**: 100% of approved merchants have a completed verification, screening, and risk review record before activation.
- **SC-009**: 100% of onboarding records containing personal data follow the defined retention and access rules for the feature's target market.
