# Merchant branding discovery

Status: backend implementation authorized by the user; admin dashboard follows backend validation.

Source: https://github.com/elegant-software/zack-pay/issues/204

## Agreed scope

- White labeling applies inside the shared shopper app. The installed app name and icon remain unchanged.
- Issue #204 covers branding. Featured placement and feature controls will be scoped as separate linked tasks.
- Platform admins own branding changes initially. Merchants cannot edit these settings in the first version.
- Branded shopper surfaces are the merchant page, menu, cart, and checkout. Discovery, account screens, and navigation retain platform branding. Invoices and web storefront are deferred.
- Branches inherit merchant branding and may override individual values. Missing branch values fall back to merchant values, then platform defaults.
- Admin settings initially comprise display name, logo, primary color, accent color, and whether merchant branding is enabled. Layouts are predefined and text colors must remain readable. Custom fonts, arbitrary layouts, and custom CSS are deferred.
- The merchant management app retains platform branding in this release.
- Admins preview changes before saving. Save publishes immediately; Cancel discards edits. There is no separate draft or approval workflow initially.
- Shopper branding refreshes on entering a merchant or branch and stays stable throughout an active checkout.
- Disabling merchant branding disables branch overrides and restores platform styling. The merchant's ordinary name and logo remain visible.
- The branding display name affects presentation only. Legal merchant identity, invoices, and payment records remain unchanged.
- Invalid colors and combinations that cannot support readable text are rejected with field-level explanations. Backend validation enforces the same rules.
- Branding changes use the existing admin permission for merchant editing, enforced by the backend for both merchant and branch settings. A dedicated permission is deferred.
- The fallback platform brand is Simpleio, as explicitly selected by the user. The issue's Kasseno terminology is outdated for this design. Existing assets still need verification.

## Existing behavior

- Merchant and branch `appearances` describe scheduled locations and times, not visual branding. The issue's assumption that these endpoints support branding is incorrect.
- The backend supports merchant logo upload and attachment. The admin dashboard already exposes logo management.
- Merchant offerings already exist as business capabilities with backend enforcement; they must be distinguished from promotional placement and rollout flags.
- Persisted brand colors, white-label settings, and featured placement were not found in the inspected backend.
- Existing backend merchant/logo writes were found to require authentication without an explicit platform-admin restriction. The agreed admin-only branding policy therefore requires backend authorization work; dashboard access checks alone are insufficient.
- Shopper baskets already reset when merchant or branch changes. Branding must follow the same context boundary.
- The shopper merchant page currently falls back from branch image to merchant logo. Branch photography and a branch brand logo are distinct concepts and must not be silently conflated.
- Checkout currently consumes route-provided merchant names rather than resolved branding.

## Decisions still open

- Exact override/reset semantics and behavior when branding cannot be loaded.
- Whether hosted payment surfaces and payment/security indicators retain their required styling.
- Configuration delivery, upload handling, and integration with existing permissions and checkout behavior, pending code inspection.
- Detailed scope of the separate featured placement and feature-control tasks.

## Backend implementation choices

### Follow-up: separate mobile app themes

The user subsequently authorized independent named theme libraries for both the shopper app and merchant management app. This extends the earlier shopper-only configuration scope: the admin dashboard now manages palettes separately for each mobile app, while actual mobile integration remains deferred. The `/branding` settings and their existing shopper behavior remain unchanged. See [merchant themes](../../reference/merchant-themes.md) for the app-scoped storage and API.

The backend uses the existing `blitzpay-admin` Keycloak realm role. Branding is stored independently of authentication. Complete PUT replacement makes null fields inherit; disabling preserves other values submitted in the request. Foreground colors are derived for readable text. Unique scope-bound upload keys separate branding logos from ordinary merchant logos and branch photos. See [merchant-branding API documentation](../../reference/merchant-branding.md).

Client caching/failure behavior and hosted payment styling recommendations remain pending explicit confirmation; this backend does not implement those client behaviors.
