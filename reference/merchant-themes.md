# Merchant themes

Admins manage saved, named color palettes independently for each merchant and each mobile app. The shopper app and merchant management app have separate theme libraries. Each library supports multiple themes containing a name, primary color, and accent color. Theme names must be nonblank and at most 120 characters. Colors use six-digit `#RRGGBB` values; responses normalize them to uppercase and supply readable black or white foreground colors.

All endpoints require the `blitzpay-admin` realm role:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/merchants/{merchantId}/themes` | List themes, ordered by name then ID |
| POST | `/v1/merchants/{merchantId}/themes` | Create a theme (201) |
| PUT | `/v1/merchants/{merchantId}/themes/{themeId}` | Replace a theme's name and colors |

Example request:

```json
{"name":"Summer","primaryColor":"#123ABC","accentColor":"#FFCC00"}
```

All three endpoints accept `?app=SHOPPER` or `?app=MERCHANT`. Omitting `app` defaults to `SHOPPER` for compatibility. For example, `POST /v1/merchants/{merchantId}/themes?app=MERCHANT` saves a merchant management app theme. The response includes `app`. The app scope is immutable; updating a theme through the other app's scope returns 404. Unknown app values return 400. Existing rows are backfilled as `SHOPPER` by migration `20261106-004-merchant-theme-app.sql`.

Responses include `id`, `merchantId`, `name`, `primaryColor`, `primaryTextColor`, `accentColor`, and `accentTextColor`. Themes belonging to another merchant return 404 on update. Existing merchants start with an empty library; admins can add the first theme and any subsequent palettes. Theme deletion and automatic migration of existing branding palettes are not included.

Saving a theme does not publish it as active branding or alter existing merchant/branch branding settings. Mobile theme selection and application are deferred. Scheduled `/appearances` remain location events, separate from themes.

The admin dashboard exposes separate **Shopper app branding** and **Merchant app branding** sections on the merchant profile, each with **Manage themes**. Admins can create or edit named palettes with color pickers, hex fields, and a local preview. Saving persists only to that app's library; cancelling discards edits after confirmation when dirty.

Persistence uses the Liquibase-managed `blitzpay.merchant_theme` table. Merchant deletion is restricted while theme rows exist.
