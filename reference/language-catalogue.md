# Shared language catalogue

Authenticated GET /v1/merchants/menu-languages is the single database-backed
catalogue for menu language settings and dashboard interface choices. The existing
URL and name fields remain compatible. No anonymous access is added.

Each active entry includes code, displayName, nativeName, nullable flagCode
(a representative ISO country flag), direction (ltr or rtl), adminUiEnabled,
and menuEnabled. Dashboard clients offer only adminUiEnabled entries with a shipped
UI bundle/flag asset; merchant dropdowns use menuEnabled. Interface translations
remain frontend assets. Product translations remain in the existing tables.

The catalogue lives in blitzpay.merchant_menu_languages. Liquibase seeds Arabic,
English and German as dashboard-enabled. Other menu languages remain available
without claiming that their dashboard interface has been translated. New entries
default to menu-enabled and dashboard-disabled.

Merchant originalMenuLanguage and supportedMenuLanguages remain merchant
settings. The user's dashboard language is a separate preference and never
rewrites those settings or product content. Product reads continue through
MenuLanguageService.resolve and existing per-product original-language fallback.
The browser may skip reloading a single-language merchant on UI language changes.

The former /v1/admin/ui-languages endpoint is removed before release. Both
features must consume this shared catalogue instead. Deploy the catalogue
migration/API before the updated dashboard. No existing translation data is moved.

## Existing local metadata columns

The initial metadata migration is guarded when any metadata column already exists.
A follow-up migration adds only missing columns/constraints and seeds the shipped
dashboard languages. Existing language assignments, translation text and unrelated
language metadata are preserved. The guard does not change the original changeset
SQL checksum, so databases that already recorded it continue to validate.
Reconciliation rollback is deliberately non-destructive because existing column
ownership cannot be inferred safely. Do not drop language tables or clear checksums
to recover a local duplicate-column error.
