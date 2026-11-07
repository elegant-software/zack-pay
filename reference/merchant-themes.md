# Merchant themes

Platform admins and the merchant's managers manage named palettes independently for each merchant and app. Each library supports multiple themes containing a name, primary/accent colors and optional complete light/dark semantic palettes. Theme names must be nonblank and at most 120 characters. Legacy colors use six-digit `#RRGGBB` values; responses normalize those fields to uppercase and supply readable black or white foreground colors. See [merchant white label](merchant-white-label.md) for palette roles and validation.

All endpoints require the `blitzpay-admin` realm role or an existing MERCHANT_MANAGER assignment for the requested merchant:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/merchants/{merchantId}/themes` | List themes, ordered by name then ID |
| POST | `/v1/merchants/{merchantId}/themes` | Create a theme (201) |
| PUT | `/v1/merchants/{merchantId}/themes/{themeId}` | Replace a theme's name and colors |

Example request:

```json
{"name":"Summer","primaryColor":"#123ABC","accentColor":"#FFCC00"}
```

All three endpoints accept `?app=SHOPPER`, `MERCHANT`, `STOREFRONT` or `KITCHEN`. Omitting `app` defaults to `SHOPPER` for compatibility. For example, `POST /v1/merchants/{merchantId}/themes?app=MERCHANT` saves a merchant management app theme. The response includes `app`, resolved `light` and `dark` palettes. The app scope is immutable; updating a theme through the other app's scope returns 404. Unknown app values return 400. Existing rows are backfilled as `SHOPPER` by migration `20261106-004-merchant-theme-app.sql`.

Responses include `id`, `merchantId`, `name`, `primaryColor`, `primaryTextColor`, `accentColor`, and `accentTextColor`. Themes belonging to another merchant return 404 on update. Existing merchants start with an empty library; admins can add the first theme and any subsequent palettes. Theme deletion and automatic migration of existing branding palettes are not included.

Saving a theme does not publish it as active branding or alter existing merchant/branch branding settings. Mobile theme selection and application are deferred. Scheduled `/appearances` remain location events, separate from themes.

The admin dashboard exposes **White label** on the merchant profile, with application/scope selectors, named palette creation/application, color pickers, hex fields, light/dark previews and explicit publishing. Library saves do not publish. Existing theme update endpoints remain supported; the editor saves new named variants rather than silently updating a shared palette.

Persistence uses the Liquibase-managed `blitzpay.merchant_theme` table. Merchant deletion is restricted while theme rows exist.
