# ADR-0024 — Card Reader for girocard Terminal Payments; one open Terminal Payment per Order

## Status
Accepted. Partly supersedes ADR-0007: the "no hardware" decision no longer holds.

## Context

Merchants in Germany need to accept girocard (EC card) in person (blitz-pay-mobile#54). Under ADR-0007 the merchant's phone is the only reader (**Tap to Pay**). Stripe's Germany docs list girocard only on the WisePad 3 and S700/S710 readers, not on Tap to Pay ([regional docs](https://docs.stripe.com/terminal/payments/regional?integration-country=DE)). A girocard that also carries Visa or Mastercard may pass on Tap to Pay through that network, but a girocard-only card never does.

Before this decision, the merchant app created a new PaymentIntent on every "collect" press and cancelled it on any error. If the phone lost its connection or the app died after Stripe had charged the card, the waiter saw an error, retried, and the customer was charged twice.

## Decision

1. **Card Reader next to Tap to Pay.** A Bluetooth Stripe WisePad 3 is paired to a merchant phone and driven by the same `@stripe/stripe-terminal-react-native` SDK (`bluetoothScan` discovery instead of `tapToPay`). The backend connection token, Terminal Location, `card_present` PaymentIntent and webhook settlement are unchanged. Both are **Terminal Payments**. The app uses the paired Card Reader when it can reach it and otherwise falls back to Tap to Pay.
2. **One open Terminal Payment per Order.** `create-intent` with `card_present` for an Order that already has an unfinished `STRIPE_TERMINAL` PaymentIntent returns that same intent. If the intent has already succeeded, it returns "already paid". The app does not cancel the intent on a soft decline (e.g. `online_or_offline_pin_required` → "insert card") or on a disconnect. Only an explicit staff cancel ends the intent so a new one can start. The Order's status on the backend, not the app's screen, decides whether the Order is paid.

## Considered options

- **S700/S710 smart reader (server-driven):** about €250+ per reader and a second integration style. Rejected for now.
- **Another PSP (SumUp, Adyen)** with native girocard: a new provider integration outside ADR-0005/0007. Rejected.
- **Tap to Pay only:** girocard-only cards would be declined, so it doesn't meet the requirement.
- **New intent per retry, cancelling the old ones:** if the cancel arrives after the charge, the customer is still charged twice. Rejected in favour of reusing the open intent, which Stripe can charge only once.

## Consequences

- No new backend tables. Readers are paired on the phone, not registered per Branch, and `tap_to_pay_enabled` now means "Terminal Payments enabled".
- Development and CI use the SDK's simulated Bluetooth reader. Real girocard acceptance is verified on hardware in blitz-pay-mobile#64.
- girocard has no card refunds. Stripe refunds girocard payments by SEPA transfer, which can fail. This matters once BlitzPay adds refunds.
