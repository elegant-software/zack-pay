# BlitzPay – Mobile-facing REST API Reference

**Base URL:** `https://<host>/v1`  
All endpoints use URL-path versioning (`/v1/...`). Default version is `1`.

---

## 1. Create a payment request

**`POST /v1/payments/request`**

Initiates a TrueLayer payment and returns the IDs needed to open the bank redirect.

**Request body**
```json
{
  "orderId": "order-123",
  "amountMinorUnits": 1999,
  "currency": "GBP",
  "userDisplayName": "Alice Smith",
  "redirectReturnUri": "myapp://payment-return"
}
```

---

## 1b. Create a Stripe Pay by Bank checkout session

**`POST /v1/payments/stripe/pay-by-bank/create-checkout-session`**

Creates a hosted Stripe Checkout Session for the Pay by Bank flow on the merchant's connected account.

**Request body**
```json
{
  "orderId": "ORDER-123",
  "merchantId": "00000000-0000-0000-0000-000000000001",
  "branchId": "00000000-0000-0000-0000-000000000002",
  "successUrl": "blitzpay://payment/success",
  "cancelUrl": "blitzpay://payment/cancel"
}
```

Optional fields:

- `amount`: overrides the order total when needed
- `currency`: defaults to the order currency; only `EUR` and `GBP` are accepted by this route
- `productId`: used for branch/price resolution if the client does not send a branch directly

**Response `200 OK`**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "checkoutUrl": "https://checkout.stripe.com/pay/cs_test_123",
  "checkoutSessionId": "cs_test_123",
  "connectedAccountId": "acct_123"
}
```

**Errors**
- `400 Bad Request` — invalid request, unsupported currency, or missing `successUrl`
- `404 Not Found` — order not found
- `409 Conflict` — order is not payable
- `503 Service Unavailable` — Stripe Pay by Bank is not enabled for the merchant

**Response `202 Accepted`**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "paymentId": "tl-payment-id",
  "resourceToken": "tl-resource-token",
  "redirectReturnUri": "https://payment.truelayer.com/..."
}
```

---

## 2. Stream payment status (SSE)

**`GET /v1/qr-payments/{paymentRequestId}/events`**  
`Accept: text/event-stream`

Opens a Server-Sent Events stream that pushes `PaymentResult` updates for the given `paymentRequestId`. Auto-closes after 5 minutes of inactivity.

**SSE event shape**
```
event: payment
id: <paymentRequestId>
data: { "paymentRequestId": "...", "paymentId": "...", "status": "...", ... }
```

---

## 3. Register device for push notifications

**`POST /v1/devices`**

Registers (or refreshes) an Expo push token so the device receives payment status push notifications.

**Request body**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
  "platform": "IOS"
}
```

| Field | Required | Notes |
|---|---|---|
| `paymentRequestId` | yes | UUID of an existing payment request |
| `expoPushToken` | yes | Must match `ExponentPushToken[...]` |
| `platform` | no | `IOS` or `ANDROID` |

**Response `201 Created`** (new) or **`200 OK`** (refresh)
```json
{
  "id": "uuid",
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "expoPushToken": "ExponentPushToken[...]",
  "platform": "IOS"
}
```

**Errors**
- `404 Not Found` — `paymentRequestId` does not exist

---

## 4. Unregister device

**`DELETE /v1/devices/{expoPushToken}`**

Removes the push token. Call on logout or in response to a user privacy request.

**Response `204 No Content`**

---

## 5. Fetch payment status (fallback poll)

**`GET /v1/payments/{paymentRequestId}`**

Returns the authoritative current status stored server-side. Use as a fallback when SSE or push is unavailable.

**Response `200 OK`**
```json
{
  "paymentRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SETTLED",
  "terminal": true,
  "lastEventAt": "2026-04-15T14:23:00Z"
}
```

**Status lifecycle**

```
PENDING → EXECUTED → SETTLED (terminal)
                   → FAILED  (terminal)
                   → EXPIRED (terminal)
```

`terminal: true` means no further status changes will occur.

**Errors**
- `400 Bad Request` — `paymentRequestId` is not a valid UUID
- `404 Not Found` — no status record yet (payment too new or unknown)

---

## 6. Ingest mobile logs

**`POST /v1/observability/mobile-logs`**

Sends batched log events from the app; the server forwards them to the OTLP/Loki pipeline.

**Request body**
```json
{
  "context": {
    "serviceName": "blitzpay-mobile",
    "serviceVersion": "1.2.0",
    "environment": "production",
    "sessionId": "sess-abc",
    "osName": "iOS",
    "osVersion": "17.4"
  },
  "events": [
    {
      "timestamp": "2026-04-15T14:23:00.000Z",
      "severityText": "ERROR",
      "message": "Payment flow failed",
      "attributes": { "screen": "PaymentScreen" },
      "exception": {
        "type": "NetworkError",
        "message": "timeout",
        "stack": "...",
        "isFatal": false
      }
    }
  ]
}
```

`context` is optional. Each event only requires `message`. All other fields are optional.

**Response `202 Accepted`**
```json
{ "accepted": 1 }
```

---

## 7. Geofencing and Nearby Merchants

### Recommended mobile flow

Use the APIs for two distinct jobs:

- **Geofencing**: background enter/exit detection
- **Nearby merchant discovery**: foreground lookup based on the user's current location

These are related, but they are not the same thing.

### A. Fetch geofence regions for device registration

**`GET /v1/geofence/regions`**

Returns active merchant and branch regions that the mobile app can register with the OS geofencing APIs.

Optional query parameters:

- `lat`
- `lng`

If the app provides `lat` and `lng`, the server sorts regions by proximity so the mobile app can choose the nearest regions within platform limits.

### B. Post enter/exit events from the mobile OS

**`POST /v1/proximity`**

The mobile app should call this endpoint when iOS or Android reports a geofence transition.

Typical use:

- `enter`: record the event and return merchant context for the triggered area
- `exit`: record the event and return `action: none`

Important: this endpoint is for a **triggered geofence event**, not for general merchant search.
It returns the merchant context for the triggered region, not a generic list of all merchants around the user.

### C. Use nearby lookup when the user is actively browsing merchants

**`GET /v1/merchants/nearby?lat=...&lng=...&radiusMeters=...`**

When the user opens a discovery screen or explicitly looks for nearby merchants, the mobile app should fetch the device's current location and call this endpoint directly.

This is the correct API for "show merchants around where the user is now".

### Current-location guidance

Do **not** treat geofence enter/exit events as a reliable substitute for the user's current location:

- geofence APIs report transitions, not continuous live position
- transition delivery can be delayed by the OS
- a geofence event tells you which region fired, not the full set of merchants around the user
- background geofencing and foreground location lookup have different power, privacy, and UX constraints

### Server-side location persistence

The server currently records **proximity events** for analytics and deduplication, but it should **not** persist a continuously updated "current user location" by default.

That is the recommended default for three reasons:

- it is not required for geofence-triggered merchant context
- it is not required for nearby lookup, because the mobile client can send fresh coordinates on demand
- it reduces privacy and retention risk

Only introduce server-side "last known user location" storage if there is a separate, explicit product requirement for:

- cross-device location continuity
- server-driven location timelines
- analytics that require exact last-known position
- asynchronous server-side matching independent of a client request

Without one of those requirements, on-demand client coordinates are the cleaner design.

---

## Push notification payload (Expo)

When a payment status changes, the server sends an Expo push notification to all registered tokens for that `paymentRequestId`. The mobile app should handle the notification and either display it directly or trigger a poll to `/v1/payments/{paymentRequestId}` for the latest state.

The push is triggered for every status transition: `PENDING` → `EXECUTED` → `SETTLED` / `FAILED` / `EXPIRED`.

---

## Source files

| Endpoint | Controller |
|---|---|
| `POST /v1/payments/request` | `payments/qrpay/PaymentRequestController.kt` |
| `GET /v1/qr-payments/{id}/events` | `payments/qrpay/QrPaymentSseController.kt` |
| `POST /v1/devices` | `payments/push/api/DeviceRegistrationController.kt` |
| `DELETE /v1/devices/{token}` | `payments/push/api/DeviceRegistrationController.kt` |
| `GET /v1/payments/{id}` | `payments/push/api/PaymentStatusController.kt` |
| `GET /v1/geofence/regions` | `merchant/web/GeofenceController.kt` |
| `POST /v1/proximity` | `merchant/web/ProximityController.kt` |
| `GET /v1/merchants/nearby` | `merchant/web/MerchantLocationController.kt` |
| `POST /v1/observability/mobile-logs` | `mobileobservability/MobileLogsController.kt` |
