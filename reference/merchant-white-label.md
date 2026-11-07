# Merchant white label

Issue: https://github.com/elegant-software/zack-pay/issues/204

This extends the original two-color branding scope. Implementation is limited to the backend and the admin dashboard. Mobile apps, the web storefront/PWA and Electron kitchen have been reviewed for their configuration needs but are not modified. Publishing configuration does not yet change those clients.

## Configuration and inheritance

Each merchant has independent `SHOPPER`, `MERCHANT`, `STOREFRONT` and `KITCHEN` settings. Branches inherit the corresponding merchant app configuration. A branch can override display name, page/app title, default logo, dark logo, appearance preference and either complete color palette. No industry-specific UI layout or arbitrary CSS is stored. Restaurants and service businesses use the existing merchant vertical and offering model.

Resolution: platform defaults → merchant app → branch app. The existing `/branding` configuration is retained as the fallback for `SHOPPER` only. A merchant's explicit `enabled: false` blocks branch branding. Disabling restores platform colors and ordinary merchant identity, retaining saved settings for later use. Null optional fields inherit; PUT replaces the entire local settings object. Resetting a branch removes local values; resetting merchant settings returns to platform/legacy shopper defaults. Empty strings are invalid; send null to clear an identity field.

`title` and `displayName` are presentation fields. They never change legal business identity, invoices, authentication or payment records. Runtime configuration cannot change installed native app names/icons, app-store listings, package IDs, domains, PWA manifests or Electron packaging. Those require separate release/hosting integration.

## API

Base: `/v1/merchants/{merchantId}/white-label`, or `/v1/merchants/{merchantId}/branches/{branchId}/white-label`.

| Method | Suffix | Purpose |
| --- | --- | --- |
| GET | `?app=SHOPPER` | Editor settings, revision, effective values, inherited configuration, enabled offering codes and logo previews |
| PUT | `?app=SHOPPER` | Publish `{ "settings": {...}, "revision": n }` |
| GET | `/effective?app=STOREFRONT` | Resolved configuration without editor state or storage keys |
| POST | `/logo/upload-url` | Scope-bound PNG/JPEG/WebP upload, `{ "contentType": "image/png" }` |

App defaults to SHOPPER. Editor and upload access require the verified `blitzpay-admin` realm role or an existing MERCHANT_MANAGER assignment for the requested merchant. Employees, shoppers and managers of other merchants cannot edit. Anonymous reads are limited to the resolved STOREFRONT response; other app reads require authentication. Responses use `Cache-Control: no-store`, including presigned image URLs. Clients must not treat expiring image URLs as permanent asset identifiers.

Use the editor response's revision when publishing. A missing record has revision `-1`; a newly stored record has revision `0`. Stale revisions, concurrent inserts and optimistic-lock conflicts return 409. Reload, review and explicitly republish; never silently retry an overwritten form. The database has separate unique indexes for merchant and branch app scopes.

Logos reuse the existing unique branding upload keys. An upload never overwrites a currently published object. Save the returned storage key only after upload succeeds; publishing attaches it. A logo must belong to the same merchant and branch scope. The dashboard caps chosen files at 5 MB; storage-level upload size enforcement is not added here. Unpublished/orphaned upload cleanup remains an object-storage lifecycle concern.

## Color roles and accessibility

Each non-null palette must supply all roles: `primary/onPrimary`, `accent/onAccent`, `background/onBackground`, `surface/onSurface`, `card/onCard`, `muted`, `border`, `error/onError`, `success/onSuccess`, `warning/onWarning`. All colors are six-digit opaque hex values. Set `light` or `dark` to null to inherit that entire palette. `themeMode` is SYSTEM, LIGHT or DARK; SYSTEM allows consumers to follow the device preference and user choice.

Foreground pairs and muted text on background/surface/card need at least 4.5:1 contrast. Control borders need at least 3:1 against those surfaces. The server enforces the same checks as the editor and identifies the failing mode/role in its validation detail. Consumers must preserve a visible focus indicator, use labels/icons alongside status colors and test actual components: valid tokens alone do not guarantee an accessible application.

These roles follow [Material's semantic color-role approach](https://m3.material.io/styles/color/roles). Contrast thresholds follow [WCAG 2.2](https://www.w3.org/TR/WCAG22/), criteria 1.4.3 and 1.4.11.

## Themes and features

The existing `/v1/merchants/{merchantId}/themes?app=...` library now supports all four apps and optional complete light/dark palettes. Old primary/accent fields and existing clients remain supported. Old themes resolve their primary/accent into a light palette and use the platform dark palette. Platform admins and the merchant's managers can manage themes. Applying a library theme copies its palettes into the local editor draft. Publishing is explicit; later library edits never mutate published profiles.

`hiddenFeatures` contains active offering codes, not permissions or subscription entitlements. Effective features are active catalogue entries assigned to the merchant, minus merchant and branch exclusions. Branches may only hide more. Revoked or inactive offerings disappear even if an older profile references them. Hiding remains independent of the visual-branding toggle. Existing ordering, booking, deferred-payment and language prerequisites remain enforced by their owning services.

## Admin workflow

Open a merchant profile → **White label**. Select application and merchant defaults or branch, edit on the dedicated settings page, preview light and dark, then **Publish changes**. Missing fields inherit; palette edits copy the current inherited palette before changing a role. Errors stay beside Publish. App/scope changes, route navigation and browser unload guard unsaved edits. Discard changes restores the saved settings while keeping the page open; a successful upload or palette-library save does not publish the profile.

Merchant managers enter through `/merchant-branding/{merchantId}` on the dashboard host (optional `?environment=local`, `k8s-staging` or `aws-staging`). This standalone route checks a valid token and backend merchant-manager authorization before opening the editor. Existing admin pages retain their platform-admin gate and shell. Managers do not receive access to unrelated dashboard pages.

## Consumer integration contract

Future client work should fetch the effective app/merchant/branch config on entering context, validate the response, and map semantic roles into native/web theme providers. Restore defaults on leaving a merchant context. Honor device/user appearance preference, use the dark logo with default-logo fallback and pin a configuration snapshot during an active checkout. Feature visibility improves presentation; authorization and capabilities must always be enforced server-side. A shared marketplace retains platform identity outside merchant-scoped surfaces. Hosted payment/security UI retains provider-required behavior. Each consumer needs integration and accessibility tests before rollout.

Schema migration: `20261107-001-create-merchant-white-label.sql`. No existing branding data is rewritten. The new table and theme JSON columns use JPA, not reporting jOOQ queries, so the reporting generation allowlist is unchanged.

Verification includes a PostgreSQL 16 Testcontainers test that applies the full changelog, validates the Hibernate schema, writes all four theme/app scopes, round-trips JSON settings, checks revision increments and rejects duplicate merchant/app profiles. It skips when Docker is unavailable. Rolling back the app-catalog constraint requires first removing or migrating any STOREFRONT/KITCHEN library rows; rollback must not silently discard those palettes.
