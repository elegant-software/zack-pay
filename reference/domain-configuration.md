# Domain Configuration (Kubernetes)

Every public hostname of a Kubernetes environment (API, Keycloak, admin dashboard, storefront,
MinIO) is derived from **one value**: `global.baseDomain` in `helm/blitz-pay/values-<env>.yaml`.
Changing the domain means editing that line and redeploying. Nothing else in the `blitz-pay` or
`blitzpay-admin-dashboard` repos hardcodes it. Decision record: [ADR-0018](../docs/adr/0018-single-base-domain.md).

## How it works

```
helm/blitz-pay/values-staging.yaml          ← the one place you edit
  global.baseDomain: zackpay.de
          │
          ▼  backend Helm deploy (zack-pay: "CD · Deploy to Kubernetes")
  ┌──────────────────────────────────────────────────────────────┐
  │ blitz-pay ingress, TLS secret, FRONTEND_ORIGIN (CORS),       │
  │ Stripe Connect URLs, KEYCLOAK_IAM_SERVER_URL, STORAGE_ENDPOINT│
  │                                                              │
  │ ConfigMap blitzpay-domains (namespace blitzpay-<env>)        │
  │   BASE_DOMAIN, API_HOST, AUTH_HOST, ADMIN_HOST,              │
  │   STOREFRONT_HOST, MINIO_HOST, MINIO_CONSOLE_HOST,           │
  │   TLS_SECRET_NAME                                            │
  └──────────────────────────────────────────────────────────────┘
          │ read by
          ├─► k8s/apply-infra.sh            → Keycloak, Backstage, MinIO ingresses
          ├─► dashboard deploy workflow     → admin + storefront ingress hosts
          └─► dashboard/storefront containers at start
                → admin: /config.js (API, Keycloak, storefront hosts for the browser)
                → storefront: nginx proxy target (API_HOST)
```

`blitzpay-domains` is a plain Kubernetes ConfigMap: a key/value list stored in the cluster. Only
the backend Helm chart writes it (`helm/blitz-pay/templates/domains-configmap.yaml`); never edit
it by hand, the next deploy overwrites it.

### Hostname rule

Every hostname is `<subdomain>.<baseDomain>`. Subdomains are fixed labels in `global.hosts`
(defaults in `values.yaml`, per-environment overrides in `values-<env>.yaml`):

| Key | Staging | Production |
|---|---|---|
| `api` | `api-blitzpay-staging` | `api-blitzpay` |
| `auth` | `auth` | `auth` |
| `admin` | `admin` | `blitzpay-admin-dashboard` |
| `storefront` | `storefront` | `storefront` |
| `minio` / `minioConsole` | `minio` / `minio-console` | (not used) |

The shared TLS secret is `wildcard-<baseDomain with dots as dashes>-tls`, e.g.
`wildcard-zackpay-de-tls`. `ingress.hostname`, `ingress.tlsSecretName`, `config.frontendOrigin`
and `config.stripeConnect*` still exist as explicit overrides but must stay empty in staging and
production. Setting them breaks the single source again.

## Changing the domain

1. **DNS (manual, once per domain):** add the domain to Cloudflare and point a wildcard record
   `*.<newdomain>` at the cluster. DNS must resolve *before* deploying, or cert-manager cannot
   issue TLS certificates for the new hosts.
2. **Keycloak (manual):** in realm `blitzpay`, add `https://<admin host>/*` to the
   `blitzpay-admin-dashboard` client's Valid Redirect URIs and `https://<admin host>` to its
   Web Origins. Keycloak's own public hostname follows the ingress, so tokens are issued for the
   new `auth.<newdomain>` automatically.
3. **Edit** `global.baseDomain` in `helm/blitz-pay/values-<env>.yaml`, commit, push to `main`.
4. **Deploy the backend:** run *CD · Deploy to Kubernetes* in the `zack-pay` repo
   (environment `staging`). This updates the backend and the `blitzpay-domains` ConfigMap.
5. **Apply infra ingresses:** `k8s/apply-infra.sh` (Keycloak, Backstage, MinIO).
6. **Redeploy dashboard and storefront:** run *CD · Deploy to Kubernetes* in the
   `blitzpay-admin-dashboard` repo, once with `manifests_dir: k8s` and once with
   `k8s/storefront` (or publish a release, which deploys both). No image rebuild is needed:
   the containers read the new hosts at start.

Order matters: the backend must deploy first. The dashboard deploy fails with
`blitzpay-domains ConfigMap missing` if the ConfigMap does not exist yet.

## Verifying

```bash
# The values every consumer reads
kubectl get configmap blitzpay-domains -n blitzpay-staging -o yaml

# Backend derived config
kubectl get configmap -n blitzpay-staging -l app.kubernetes.io/name=blitz-pay -o yaml \
  | grep -E 'FRONTEND_ORIGIN|KEYCLOAK_IAM_SERVER_URL|STORAGE_ENDPOINT|STRIPE_CONNECT'

# Infra ingresses: dry-run shows the rendered hosts without applying
DRY_RUN=1 k8s/apply-infra.sh | grep -E 'host:|secretName'

# Dashboard runtime config: must list the new hosts
curl -s https://<admin host>/config.js

# CORS preflight: expect 200 with a matching access-control-allow-origin
curl -s -o /dev/null -D - -X OPTIONS https://<api host>/v1/merchants \
  -H "Origin: https://<admin host>" -H "Access-Control-Request-Method: GET" \
  | grep -i "^HTTP\|allow-origin"
```

`k8s/apply-infra.sh` printing `unchanged` is expected when the domain did not change.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Dashboard login shows `error=keycloak_init_failed` | `/config.js` has the wrong `authHost`, or the Keycloak client lacks the admin origin (step 2) |
| Browser calls fail with 403 on preflight | `FRONTEND_ORIGIN` does not match the admin host; check `global.hosts.admin` and that `config.frontendOrigin` is empty |
| Certificate warning on a new host | DNS was not resolving when the ingress was applied; cert-manager retries, or delete the `Certificate` to force reissue |
| Dashboard deploy: `blitzpay-domains ConfigMap missing` | Backend not yet deployed to that namespace |
| `apply-infra.sh`: `unresolved placeholders` | A manifest uses a `${KEY}` that `blitzpay-domains` does not publish; add it to `domains-configmap.yaml` |

## Not covered

These keep their own domain settings and must be updated separately:

- **AWS deployment:** `infra/lib/*.ts` (CDK) and the dashboard's `aws-staging` API URL `api.zackpay.de`.
- **oracle-dev:** `values-oracle-dev.yaml` and `application-oracle-dev.yml` set explicit `oc-*` URLs
  and no `baseDomain`.
- **Mobile apps** (`blitz-pay-merchant`, shopper app): `.env` / `eas.json`, compiled into store builds;
  a domain change needs a new build or OTA update.
- **Local dev defaults:** `application.yml` placeholder defaults and the dashboard's
  `public/config.js` (overwritten in Kubernetes, used only by `ng serve` and tests).
- **`cluster-issuer.yml`:** the ACME contact email is not a hostname.
