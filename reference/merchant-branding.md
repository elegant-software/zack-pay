# Merchant branding

Issue: https://github.com/elegant-software/zack-pay/issues/204

This documents the legacy shopper `/branding` API, which remains compatible. New app-scoped identity, light/dark palettes and merchant-manager editing use the [white-label API](merchant-white-label.md). Its SHOPPER fallback reads these legacy settings when no app profile overrides them.

Branding is distinct from scheduled merchant appearances, branch photography, business offerings, and legal merchant identity. Simpleio is the fallback platform brand. This backend release does not change mobile app binaries, invoices, or hosted payment pages.

## API

All requests require a valid access token from the configured Keycloak realm. PUT and logo-upload requests additionally require the **realm role `blitzpay-admin`**; client roles and merchant manager assignments do not grant branding access.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/merchants/{merchantId}/branding` | Merchant settings and resolved branding |
| PUT | `/v1/merchants/{merchantId}/branding` | Replace merchant settings |
| POST | `/v1/merchants/{merchantId}/branding/logo/upload-url` | Create a merchant branding logo upload |
| GET | `/v1/merchants/{merchantId}/branches/{branchId}/branding` | Branch overrides and resolved branding |
| PUT | `/v1/merchants/{merchantId}/branches/{branchId}/branding` | Replace branch overrides |
| POST | `/v1/merchants/{merchantId}/branches/{branchId}/branding/logo/upload-url` | Create a branch branding logo upload |

PUT body example:

```json
{
  "enabled": true,
  "displayName": "Example Cafe",
  "logoStorageKey": null,
  "primaryColor": "#123456",
  "accentColor": "#F0AB00"
}
```

PUT replaces the entire configuration: omitted or null fields reset to inheritance. Submit all settings you intend to keep. `enabled` defaults to false at merchant level. At branch level, null inherits the merchant toggle; false disables custom branding for that branch. A branch cannot enable branding when the merchant toggle is off. Disabling branding preserves the other saved fields when included in the PUT body.

The response has `settings` (the saved overrides) and `effective` (resolved shopper presentation). Branch fields inherit merchant fields, then platform defaults. The fallback palette matches the current shopper palette: primary `#00C2FF`, accent `#5856D6`. The platform name is Simpleio; its bundled logo remains a client asset. The effective merchant display name and logo fall back to the ordinary merchant name and logo. Branch photography is never used as a brand logo.

`logoPreviewUrl` is a temporary download URL for the current scope's saved custom logo, including when branding is disabled. Admin previews use it without changing effective shopper branding; null means this scope has no custom logo.

Colors must be six-digit hex values. The backend selects black or white foregrounds independently for each color using relative luminance, providing WCAG AA text contrast of at least 4.5:1. Clients must pair `primaryColor` with `primaryTextColor` and `accentColor` with `accentTextColor`; the two background colors are not approved as text colors over one another. Invalid input returns 400; unknown merchants, branches, or a branch belonging to another merchant return 404. Non-admin writes return 403.

## Logo uploads and publication

POST `{"contentType":"image/png"}` to the appropriate upload endpoint (`image/jpeg` and `image/webp` are also supported). Upload to the returned presigned URL, then include its `storageKey` in the settings PUT. Keys are unique per upload and scoped to merchant and optional branch. Unsaved uploads cannot overwrite a published logo. Branding logos are separate from the existing ordinary merchant-logo endpoints.

Saving publishes immediately. Admin preview is performed locally before saving; there is no persisted draft. Effective logo URLs are temporary presigned download URLs and should be refreshed by fetching branding again. Do not treat a presigned URL as a permanent asset identifier.

The shopper integration should fetch branding on merchant/branch entry and hold it stable through checkout. Caching and checkout snapshots are client responsibilities and are not implemented by these endpoints. The merchant app keeps platform styling. Admin dashboard and shopper integration follow this backend change.

## Schema

Liquibase migration `20261106-002-merchant-branding.sql` adds nullable branding columns to `blitzpay.merchants` and `blitzpay.merchant_branches`. Existing rows resolve to disabled custom branding without a data backfill. Legal merchant names and existing logos are not rewritten.
