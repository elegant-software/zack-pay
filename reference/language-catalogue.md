# Storefront interface language catalogue

GET /v1/storefront/languages is a public read-only guest-bootstrap endpoint.
It returns code, English displayName, nativeName, representative flagCode and
direction only. It is needed before the guest creates an order.

System-owned metadata ships in src/main/resources/i18n/storefront-languages.json.
Names and translations come from Spring MessageSource bundles in
src/main/resources/i18n/messages*.properties. Add or change interface languages
through reviewed resource changes coordinated with shipped storefront UI bundles.
No language metadata columns, reference-data writes or database migrations are added.

The browser uses the existing same-origin /v1/ nginx proxy, as for Stripe capability
discovery. Language selection needs no order token. Guest payment and order reads
retain their order-scoped HttpOnly guest_token checks. POST, PUT, PATCH and DELETE
on the language endpoint remain protected, as does /v1/merchants/menu-languages.

Merchant original/supported menu language settings and runtime-authored product
translations keep their existing storage and resolution behavior. UI preference
does not rewrite them. This endpoint does not expose admin capabilities, merchant
settings, order data or payment credentials.

Backend subtask: #220, parent elegant-software/blitzpay-admin-dashboard#85.
Deploy this endpoint before enabling backend-driven storefront language choices.
