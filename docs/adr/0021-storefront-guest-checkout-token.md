# ADR-0021 — Web storefront guests pay with an order-scoped token in a session cookie

## Status
Accepted

## Context

The web storefront (`blitz-pay-web` in the admin dashboard repo) lets people browse a merchant's catalog
without logging in. Checkout then called `POST /v1/orders` and `/v1/payments/stripe/**`, which need a
Keycloak JWT, so guest payment could not work. Opening those endpoints with `permitAll` would be unsafe:
they accept `merchantId`, `branchId` and `successUrl` from the client and have no notion of who owns an
order (blitzpay-admin-dashboard#35).

## Decision

Guests get their own small public surface under `/v1/storefront/**`. The shopper endpoints are unchanged.

- `POST /v1/storefront/orders` creates a `GUEST` order priced from the catalog (same path as shopper
  orders, plus caps of 50 lines and quantity 99). It issues a **guest token**: 256 random bits, bound to that
  one order, valid for 60 minutes. Only its SHA-256 hash is stored, on `order_orders`.
- The token travels **only** as a `guest_token` cookie: `HttpOnly; Secure; SameSite=Lax; Path=/v1/storefront`,
  with no `Max-Age`. It is never in a response body, so storefront JavaScript (and therefore XSS) cannot read it.
- `GET /v1/storefront/orders/{orderId}` and `POST /v1/storefront/orders/{orderId}/payment` require the cookie.
  A missing, wrong or expired token returns the same 404 as an unknown order, so the endpoints are not an
  order-ID oracle.
- Payment takes only `method` (`CARD` or `PAY_BY_BANK`). Amount, merchant, branch and connected account
  all come from the order. Pay by Bank redirect URLs are built from `STOREFRONT_URL` (derived from
  `global.baseDomain`), never from the request, which rules out an open redirect.
- At most five payment attempts per guest order, to limit card testing through one cookie.
- The Stripe logic that used to live in `StripePaymentController` is now `StripeCheckoutService`, shared by
  both controllers. The Stripe webhook remains the only thing that marks an order paid.

## Consequences

- "Closes when the browser closes" is only true on the client. The server can't see a browser close, and
  session restore in Chrome and Firefox can bring the cookie back, so the 60-minute server TTL is the real bound.
- CSRF is disabled globally (stateless JWT API). The cookie-authorised POST relies on `SameSite=Lax` (no
  cookie on cross-site POST) and on JSON bodies needing a CORS preflight. A forged request could at worst
  start a payment for the victim's own order and couldn't read the response.
- Not covered here and still needed before heavy traffic: per-IP rate limits and a captcha (Turnstile) on
  order creation, Radar velocity rules, and cleanup of expired unpaid guest orders.

## Considered Options

- **`permitAll` on the existing shopper endpoints:** no order ownership, client-controlled merchant and
  redirect URLs.
- **Keycloak token for guests:** Keycloak has no anonymous login. That leaves auto-created users (realm
  pollution) or a client secret in the browser (public, so no protection). Transient users only exist for
  identity brokering.
- **Token in `sessionStorage` sent as a header:** scoped to the tab, but readable by any script on the page.
