# Stripe Connect Onboarding

BlitzPay uses **Stripe Connect** with **Express** connected accounts. Setup has two layers:

| Layer | Who | How often | What |
|---|---|---|---|
| **Platform** | BlitzPay operator | Once per Stripe mode (test / live) | Enable Connect, API keys, webhooks, env vars |
| **Connected account** | Each merchant | Once per merchant | Hosted Stripe onboarding, started from the BlitzPay API. Fully automated |

Code lives in the `payments.stripe` module (`src/main/kotlin/com/elegant/software/blitzpay/payments/stripe/`).

---

## Part 1 — Platform setup (one-time)

Do this in **test mode** for staging and repeat it in **live mode** for production. Each mode has
its own keys and webhook secrets.

### 1.1 API keys

Dashboard → **Developers → API keys**

| Stripe value | Env var | Where it goes |
|---|---|---|
| Secret key (`sk_test_…` / `sk_live_…`) | `STRIPE_SECRET_KEY` | k8s Secret `blitz-pay-secrets` |
| Publishable key (`pk_test_…` / `pk_live_…`) | `EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY` | Backend env + shopper/merchant apps |

### 1.2 Enable Connect

Dashboard → **Connect → Get started**

1. Choose the **platform / marketplace** model, where merchants are connected accounts and BlitzPay is the platform.
2. Fill in the **platform profile** and **branding** (name, icon, colour). Stripe blocks account creation until this is done.
3. **Settings → Payment methods**: enable the methods the backend requests for every connected account:
   - Cards (`card_payments`)
   - Pay by Bank (`pay_by_bank_payments`). Availability depends on the platform country.
   - Transfers (`transfers`) is always available.
4. *(Optional)* **Terminal**: enable it if merchants will use Tap to Pay (`card_present`).

### 1.3 Platform account ID

Dashboard → **Settings → Account details**. Copy the `acct_…` ID.

- Env var: `STRIPE_DASHBOARD_PLATFORM_ACCOUNT_ID`
- Set in `helm/blitz-pay/values.yaml` → `config.stripeDashboardPlatformAccountId`
  (rendered into the ConfigMap). Update it whenever the Stripe platform account changes.

### 1.4 Webhook endpoints

Dashboard → **Developers → Webhooks → Add endpoint**. Create **two** endpoints. Each endpoint
has its own signing secret (`whsec_…`).

| | Platform endpoint | Connect endpoint |
|---|---|---|
| **Listen to** | Events on *Your account* | Events on *Connected accounts* |
| **Path** | `/v1/webhooks/stripe` | `/v1/webhooks/stripe/connect` |
| **Events** | `payment_intent.succeeded`<br>`payment_intent.payment_failed`<br>`payment_intent.canceled` | `account.updated`<br>`payment_intent.succeeded`<br>`payment_intent.payment_failed`<br>`payment_intent.canceled` |
| **Secret env var** | `STRIPE_WEBHOOK_SECRET` | `STRIPE_CONNECT_WEBHOOK_SECRET` |

Full URLs for `zackpay.de`:

| Environment | Stripe mode | Platform endpoint | Connect endpoint |
|---|---|---|---|
| Staging | Test | `https://api-blitzpay-staging.zackpay.de/v1/webhooks/stripe` | `https://api-blitzpay-staging.zackpay.de/v1/webhooks/stripe/connect` |
| Production | Live | `https://api-blitzpay.zackpay.de/v1/webhooks/stripe` | `https://api-blitzpay.zackpay.de/v1/webhooks/stripe/connect` |

Hosts are derived from `global.baseDomain` + `global.hosts.api` in `helm/blitz-pay/values-<env>.yaml`.
See [`domain-configuration.md`](domain-configuration.md).

> The Connect endpoint **must** include the `payment_intent.*` events. Payments are created on the
> merchant's connected account, so their events are delivered to the Connect endpoint, not
> the platform one. Without them, orders are never marked paid or failed.

### 1.5 Connect redirect URLs (derived, no action needed)

| Env var | Default | Purpose |
|---|---|---|
| `STRIPE_CONNECT_RETURN_URL` | `https://<api-host>/stripe-connect/return` | Where Stripe sends the merchant after onboarding |
| `STRIPE_CONNECT_API_BASE_URL` | `https://<api-host>` | Base for the onboarding `refresh_url` |
| `STRIPE_CONNECT_APP_DEEP_LINK` | empty | Optional. If set, the return page reopens the merchant app |
| `STOREFRONT_URL` | `https://<storefront-host>` | Web storefront origin. Guest Pay by Bank redirects are built from it ([ADR-0021](../docs/adr/0021-storefront-guest-checkout-token.md)) |

The Helm chart derives the first two and `STOREFRONT_URL` from the base domain. Leave `config.stripeConnectReturnUrl`
and `config.stripeConnectApiBaseUrl` empty in values files.

### 1.6 Store the secrets

Add the Stripe values to the `blitz-pay-secrets` k8s Secret (template in the comment block of
`helm/blitz-pay/values.yaml`):

```bash
--from-literal=STRIPE_SECRET_KEY="sk_test_..." \
--from-literal=STRIPE_WEBHOOK_SECRET="whsec_..." \
--from-literal=STRIPE_CONNECT_WEBHOOK_SECRET="whsec_..." \
```

For oracle-dev, `helm/oracle-dev-secrets.sh` prompts for these values.

If a webhook secret is blank, the matching endpoint rejects every event, so set both.

#### Updating an existing secret from `.env`

`blitz-pay-secrets` also holds the database, TrueLayer and Keycloak credentials. **Patch** it with a
merge patch, which only replaces the keys you send. Never delete and recreate it. The values below
come from the local `.env` and are never printed:

```bash
python3 -I - .env <<'EOF' | kubectl -n blitzpay-staging patch secret blitz-pay-secrets --type merge --patch-file /dev/stdin
import json, re, sys
keys = ["STRIPE_SECRET_KEY", "EXPO_PUBLIC_STRIPE_PUBLISHABLE_KEY", "STRIPE_WEBHOOK_SECRET", "STRIPE_CONNECT_WEBHOOK_SECRET"]
vals = {}
for line in open(sys.argv[1]):
    m = re.match(r'\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$', line.rstrip("\n"))
    if m and m.group(1) in keys:
        v = m.group(2).strip()
        if len(v) >= 2 and v[0] == v[-1] and v[0] in "'\"":
            v = v[1:-1]
        vals[m.group(1)] = v
missing = [k for k in keys if not vals.get(k)]
if missing:
    sys.exit("missing in .env: " + ", ".join(missing))
print(json.dumps({"stringData": vals}))
EOF
```

- Use test-mode values for `blitzpay-staging` and live-mode values for `blitzpay-prod`.
- Do **not** copy `STRIPE_CONNECT_RETURN_URL` / `STRIPE_CONNECT_API_BASE_URL` from `.env`. Locally
  they point to `localhost`; in the cluster the chart derives them (§1.5).
- `STRIPE_DASHBOARD_PLATFORM_ACCOUNT_ID` is not a secret. It goes in `values.yaml` (§1.3).

Check that every key is present without revealing it, then restart. Pods read `envFrom` only at startup:

```bash
kubectl -n blitzpay-staging get secret blitz-pay-secrets -o json \
  | jq -r '.data | to_entries[] | select(.key|test("STRIPE")) | "\(.key) set, \(.value|@base64d|length) chars"'
kubectl -n blitzpay-staging rollout restart deploy/blitz-pay
kubectl -n blitzpay-staging rollout status deploy/blitz-pay --timeout=10m
```

Finally, use **Send test webhook** in the Stripe Dashboard on both endpoints and confirm the
events are accepted (HTTP 200).

### 1.7 Activate live mode

Live keys work only after the platform account is activated (business details, bank account,
identity verification). Staging can run on test mode without activation.

---

## Part 2 — Merchant onboarding (per merchant, automated)

No manual Stripe Dashboard work is needed per merchant. Merchants enter their own business, bank
and identity details on Stripe's hosted page, so BlitzPay never handles that data.

### Flow

```
Merchant app / admin dashboard
   │ POST /v1/merchants/{merchantId}/stripe-connect
   ▼
Backend ── creates Express account (card_payments, transfers, pay_by_bank_payments)
   │       stores acct_… in stripe_connect_account_id
   │       returns { url } (Stripe Account Link)
   ▼
Merchant completes Stripe hosted onboarding
   │ link expired? → GET /v1/merchants/{merchantId}/stripe-connect/refresh (new link)
   ▼
Stripe redirects to /stripe-connect/return (→ app deep link if configured)
   │
Stripe sends account.updated → /v1/webhooks/stripe/connect
   ▼
Backend updates chargesEnabled / detailsSubmitted / payByBankEnabled
```

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/merchants/{merchantId}/stripe-connect` | Create the connected account (if missing) and return an onboarding URL |
| `GET` | `/v1/merchants/{merchantId}/stripe-connect/refresh` | New onboarding URL (Stripe `refresh_url`) |
| `GET` | `/v1/merchants/{merchantId}/stripe-connect/status` | Onboarding state and `enabledPaymentFlows` (`CARD`, `PAY_BY_BANK`, `TAP_TO_PAY`) |
| `POST` | `/v1/merchants/{merchantId}/stripe-connect/tap-to-pay/enable` | Enable Tap to Pay. Returns `409` until the account is verified |
| `DELETE` | `/v1/merchants/{merchantId}/stripe-connect/tap-to-pay` | Disable Tap to Pay |

### When a merchant can take payments

- **Card:** `charges_enabled` is true **and** the `card_payments` capability is `active`
- **Pay by Bank:** the `pay_by_bank_payments` capability is `active`
- **Tap to Pay:** card is enabled **and** Tap to Pay has been switched on via the endpoint above

These flags are only updated by `account.updated` webhooks. If `status` never changes after
onboarding, check the Connect webhook endpoint and `STRIPE_CONNECT_WEBHOOK_SECRET` first.

---

## Switching to a new Stripe platform account

Connected accounts belong to the platform that created them. After moving to a new Stripe account:

1. Repeat **Part 1** on the new account.
2. Update `config.stripeDashboardPlatformAccountId` in `helm/blitz-pay/values.yaml`.
3. **Every merchant must onboard again.** The `stripe_connect_account_id` values in the database
   point to the old platform and stop working with the new keys. Clear them and have each merchant
   run the Part 2 flow again.

---

## Checklist

- [ ] Secret and publishable keys copied (correct mode)
- [ ] Connect enabled, platform profile and branding completed
- [ ] Cards and Pay by Bank enabled (Terminal if needed)
- [ ] `STRIPE_DASHBOARD_PLATFORM_ACCOUNT_ID` updated in `values.yaml`
- [ ] Platform webhook created → `STRIPE_WEBHOOK_SECRET`
- [ ] Connect webhook created (with `account.updated` and the three `payment_intent.*` events) → `STRIPE_CONNECT_WEBHOOK_SECRET`
- [ ] Secrets stored in `blitz-pay-secrets` and app redeployed
- [ ] Test merchant onboarded, `GET …/stripe-connect/status` shows `CARD` in `enabledPaymentFlows`
