# API error responses

Every error the API returns has the same shape, so clients (admin dashboard, storefront,
merchant and shopper apps) can handle errors without parsing message text.

```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Customer is outside the branch geofence",
  "instance": "/v1/storefront/orders",
  "code": "OUTSIDE_GEOFENCE"
}
```

| Field | Meaning | Clients may rely on it? |
|---|---|---|
| `status` | HTTP status | Yes |
| `code` | Stable machine-readable reason | **Yes — branch on this** |
| `detail` | Human-readable message, English | No — wording can change any time |

Content type is `application/problem+json`, also on SSE endpoints.

The format is Spring's built-in RFC 9457 support: `ProblemDetail`, `ErrorResponseException` and
`ResponseEntityExceptionHandler`. `code` is our one extension member (RFC 9457 §3.2).

## Throwing errors

`ApiException` is a Spring `ErrorResponseException` carrying its `ProblemDetail` and `code`, so Spring
renders it. `config/GlobalExceptionHandler` (a `ResponseEntityExceptionHandler`) maps the plain JDK
exceptions below to `ApiException`, adds the default `code`, and logs every error once. Code only throws:

| Throw | Status | `code` |
|---|---|---|
| `ApiException(HttpStatus.X, message)` | X | status name, e.g. `CONFLICT` |
| `ApiException(HttpStatus.X, message, "SPECIFIC_CODE")` | X | `SPECIFIC_CODE` |
| `NoSuchElementException` | 404 | `NOT_FOUND` |
| `IllegalArgumentException` / `require(...)` | 400 (409 if the message says "already exists") | `BAD_REQUEST` / `CONFLICT` |
| `IllegalStateException` / `check(...)` | 422 | `UNPROCESSABLE_ENTITY` |
| Framework errors (bad JSON, validation, 405, 415, …) | as Spring decides | status name |
| Anything else | 500, generic `detail` | `INTERNAL_SERVER_ERROR` |

A module-specific exception extends `ApiException` and fixes its status, e.g.
`class TableConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)`.

Don't:
- return `ResponseEntity.badRequest().body(...)` or any other hand-built error body;
- add `@ExceptionHandler` methods to controllers (the one exception: a handler that must log
  something specific, which still returns `ResponseEntity.of(ApiException(...).body).build()`);
- build a `ProblemDetail` by hand or set the content type yourself — Spring does both;
- put secrets, SQL, stack traces or internal IDs a client shouldn't see in `detail`.

## When to add a specific code

Add one when a client must react differently from other errors with the same status —
typically to show its own wording or take a different action. Otherwise the generic
status-name code is enough.

- Format: `UPPER_SNAKE_CASE`, describing the reason, not the endpoint (`OUTSIDE_GEOFENCE`, not `ORDER_POST_422`).
- A published code is part of the API contract: never rename it, reuse it for another
  meaning, or remove it without a versioned migration.
- Add it to the catalogue below and assert it in a test.

## Code catalogue

| Code | Status | Thrown by | Meaning |
|---|---|---|---|
| `LOCATION_REQUIRED` | 400 | `OrderService` (walk-in ordering) | No customer location sent |
| `LOCATION_INACCURATE` | 400 | `OrderService` | Location accuracy above the allowed limit |
| `LOCATION_EXPIRED` | 400 | `OrderService` | Location captured too long ago |
| `OUTSIDE_GEOFENCE` | 422 | `OrderService` | Customer is outside the branch geofence |
| `INVOICE_EMAIL_CONSENT_REQUIRED` | 400 | `OrderService` (storefront guest order) | `buyerEmail` sent without `invoiceEmailConsent: true` |
| `KIOSK_BRANCH_MISMATCH` | 403 | `OrderService` (storefront guest order) | A Kiosk Login token for one Branch was used to order for another Branch |
| `KIOSK_PAYMENT_CHOICE_DISABLED` | 422 | `OrderService` | A Kiosk Order asked for cash payment, but cash is not enabled in the Branch's Kiosk Settings |
| `UNKNOWN_KIOSK_PAYMENT_CHOICE` | 400 | `KioskService` (Kiosk Settings) | A payment choice other than `CARD`, `CASH`, `SCAN_TO_PAY` |
| `KIOSK_PAYMENT_CHOICES_REQUIRED` | 400 | `KioskService` | `paymentChoices` is empty |
| `KIOSK_RESET_SECONDS_OUT_OF_RANGE` | 400 | `KioskService` | `resetSeconds` outside 5..300 |
| `KIOSK_LANGUAGE_NOT_SUPPORTED` | 422 | `KioskService` | A language that is not a Supported Menu Language of the Merchant |
| `KIOSK_ADVERTISED_PRODUCT_INVALID` | 400 / 422 | `KioskService` | An advertised product id that is malformed (400), or not an active Product of this Branch (422) |
| `MISSING_AUTHORIZATION`, `MISSING_AUDIO`, `UNSUPPORTED_AUDIO_FORMAT`, `PAYLOAD_TOO_LARGE`, `AUDIO_TOO_SHORT`, `AUDIO_TOO_LONG`, `NO_SPEECH_DETECTED`, `UPSTREAM_AI_ERROR` | various | `voice` module | Voice query failures |
| `INVALID_CATALOG_CURSOR` | 400 | `CatalogScope` | Malformed cursor or cursor reused for a different merchant, branch, category, or visibility |
| `CATALOG_ACCESS_DENIED` | 403 | `MerchantCatalogController` | Caller lacks branch staff/admin access to inactive products or thumbnail backfill |

## Not covered

Errors raised before a controller runs (Spring Security 401/403, unknown routes) use Spring's
default body without `code`. Clients should fall back to the HTTP status for those.
