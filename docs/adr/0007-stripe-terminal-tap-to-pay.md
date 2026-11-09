# ADR-0007: Stripe Terminal — Tap to Pay (NFC Soft Reader)

**Date:** 2026-05-28
**Status:** Accepted — partly superseded by ADR-0024 (Card Reader for girocard)

## Context

BlitzPay supports Stripe Connect (ADR-0004) for card payments via the mobile Stripe SDK. Merchants need an in-person payment option that does not require any hardware terminal — the merchant's own phone should be able to accept contactless card and wallet payments (Apple Pay, Google Pay, Visa/Mastercard/Amex contactless) directly.

Stripe Terminal offers two paths to in-person payments. The right one had to be selected, and two schema decisions had to be made.

### Terminal mode options

1. **Tap to Pay (NFC soft reader)** — the Stripe Terminal SDK is embedded in the merchant app. The merchant's phone NFC chip becomes the reader. No external hardware needed. Supported via `@stripe/stripe-terminal-react-native`. Requires Apple entitlement (`com.apple.developer.proximity-reader.payment.acceptance`) for iPhone; NFC-capable Android for Android.
2. **Hardware smart reader (server-driven)** — a physical Stripe reader (WisePOS E, S700) is registered per location. The backend pushes PaymentIntents to it via `POST /v1/terminal/readers/{reader}/process_payment_intent`. No SDK required in the merchant app.

### Terminal Location storage options

Stripe Terminal requires every reader to be registered to a Location (`loc_XXXX`). A Location maps directly to a Branch (a Branch is an operational physical location with a postal address and coordinates). Two options for storing the Location ID:

1. **Nullable column on `merchant_branches`** — `stripe_terminal_location_id VARCHAR(255)`. Lightweight, one migration. Mixes a Stripe field into the branch entity, but Terminal Location is derived entirely from Branch data and has no independent lifecycle.
2. **New `merchant_branch_stripe_terminal_locations` extension table** — follows ADR-0005's extension table pattern. One row per (Branch, Stripe Location). Correct pattern boundary but over-engineered for a single field.

### Terminal Location creation timing options

1. **Lazy — on first `ConnectionToken` request** — if `stripe_terminal_location_id` is null, call Stripe `POST /v1/terminal/locations` using the Branch's `address` and `name`, persist the returned `loc_XXXX`, then issue the token.
2. **Eager — when `tap_to_pay_enabled` is set** — create Stripe Locations for all existing branches at enable time. Incomplete: cannot cover branches added afterward, so lazy fallback is required regardless.
3. **Dedicated admin endpoint** — explicit per-branch location creation. Maximum control, maximum friction.

### Payment intent initiator options

For a Terminal payment, the Stripe Terminal SDK on the merchant's phone must call `collectPaymentMethod` on a `card_present` PaymentIntent. Two options for who creates the intent:

1. **Either app** — both the shopper app and the merchant app can call `create-intent` with a `paymentMethodType: "card_present"` flag. The Order is the coordination point: once the intent is linked to the Order, the merchant app can read the pending terminal payment from the Order and confirm it with the SDK.
2. **Merchant app only** — the merchant always initiates. Simpler, but prevents the shopper from initiating an in-person payment from their checkout screen.

---

## Decisions

1. **Tap to Pay (NFC soft reader)** is the Terminal mode — `@stripe/stripe-terminal-react-native` SDK embedded in the merchant app.
2. **Nullable column on `merchant_branches`** stores the Stripe Terminal Location ID.
3. **Lazy creation** — Terminal Location is created on first `ConnectionToken` request for a Branch; the `loc_XXXX` is persisted immediately and reused on all subsequent requests.
4. **Either app can initiate** — `create-intent` accepts `paymentMethodType: "card_present"`; the Order is the coordination point between the two apps.
5. **`tap_to_pay_enabled`** is a boolean flag on `merchant_stripe_provider_accounts`, parallel to `pay_by_bank_enabled`.
6. **`ConnectionToken` endpoint is merchant-app-facing** — called by the Terminal SDK at session start; admin has no role at runtime.

---

## Rationale

### Tap to Pay over hardware smart reader

- The explicit requirement is "phone as POS, no hardware". Tap to Pay is Stripe's only supported no-hardware path.
- Hardware smart readers require a physical device per branch. Server-driven integration (`process_payment_intent`) does not support Tap to Pay readers — it applies to WisePOS E and S700 only.
- The Terminal SDK in the merchant app is already the right host: the merchant app is installed, authenticated, and has the merchant's Connected Account context.

### Nullable column over extension table

- A Terminal Location is not a standalone entity — it is metadata derived from Branch address and name. It has no independent lifecycle, no foreign-key relationships beyond its branch, and no additional fields.
- ADR-0005's extension table pattern was designed for merchant-level PSP accounts with multiple fields and a shared `merchant_provider_account_id` FK. That pattern is disproportionate for a single nullable identifier at branch level.
- A dedicated table would require a JOIN on every `ConnectionToken` request for one value.

### Lazy Location creation over eager or explicit

- Eager creation at enable-time is incomplete: it cannot cover branches added after the merchant enables Terminal, so a lazy fallback is required regardless. If a fallback must exist, eager creation adds complexity with no net benefit.
- Lazy creation on `ConnectionToken` request is a natural fit: the request already carries `branchId`, the branch address and name are available, and the operation is idempotent (Stripe deduplicates by `display_name` + `address` if called twice).
- Explicit admin endpoint adds operational friction with no compensating benefit.

### Either app can initiate

- The shopper selecting "pay via terminal" at checkout is a valid UX that must be supported. Restricting initiation to the merchant app would require either a push-notification handshake or the shopper selecting the method then waiting with no confirmation.
- The Order already links `paymentRequestId` to the payment attempt via `orderGateway.linkPaymentAttempt()`. The merchant app reads the pending `card_present` intent from the Order — no real-time signaling between apps is needed.
- The backend change is minimal: one additional field on `CreateIntentRequest` switches `payment_method_types` from `["card"]` to `["card_present"]`. All other create-intent logic is unchanged.

### `tap_to_pay_enabled` flag

- Follows the exact same pattern as `pay_by_bank_enabled` on `merchant_stripe_provider_accounts`. No new pattern introduced.
- A merchant with a Connected Account must explicitly enable Terminal before the `ConnectionToken` endpoint will issue tokens for their branches. This prevents unverified or opted-out merchants from accidentally activating in-person acceptance.

---

## Consequences

- `merchant_stripe_provider_accounts` gains one column: `tap_to_pay_enabled BOOLEAN NOT NULL DEFAULT FALSE`.
- `merchant_branches` gains one column: `stripe_terminal_location_id VARCHAR(255)` (nullable, no FK — it is a Stripe-side identifier).
- A new endpoint `POST /v1/payments/stripe/terminal/connection-token` is added to `payments.stripe`. It:
  1. Validates the merchant has `charges_enabled = true` and `tap_to_pay_enabled = true`.
  2. Resolves the Branch's `stripe_terminal_location_id`; creates the Stripe Terminal Location lazily if absent.
  3. Calls Stripe `POST /v1/terminal/connection_tokens` scoped to the merchant's Connected Account (`Stripe-Account: acct_XXXX`) with the `location` parameter.
  4. Returns `{ secret }` to the merchant app SDK.
- `CreateIntentRequest` gains `paymentMethodType: String = "card"`. When `"card_present"`, the backend sets `payment_method_types = ["card_present"]` and `capture_method = "automatic"` on the PaymentIntent.
- `StripeConnectGateway` gains `isTapToPayEnabled(merchantId: UUID): Boolean`.
- The merchant app must include `@stripe/stripe-terminal-react-native` and request the `com.apple.developer.proximity-reader.payment.acceptance` Apple entitlement for iOS builds.
- Testing uses the Terminal SDK's built-in simulated reader (`discoverReaders({ simulated: true })`) with `setSimulatedCard('4242424242424242')`. No NFC, no entitlement, no hardware required in the test environment.
- ADR-0004 remains valid — direct charge model and Express account type are unchanged.
- ADR-0005 remains valid — `merchant_stripe_provider_accounts` is still the single source of truth for Stripe-specific merchant state.
