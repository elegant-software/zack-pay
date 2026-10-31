# ADR-0018 — Single base domain for all Kubernetes hostnames

## Status
Accepted

## Context

The staging domain was hardcoded in about 30 files across the backend Helm values, raw `k8s/`
manifests, Spring config, and the admin dashboard (environment files, nginx config, ingresses).
The dashboard baked its Keycloak and API URLs into the Angular build, so the URL lived in the
image, not the deployment.

When the domain moved from `elegantsoftware.de` to `zackpay.de`, a stale dashboard image that
still pointed at `keycloak.elegantsoftware.de` reached staging and broke admin login
(`keycloak_init_failed`). Any rename meant hunting down every copy across repos, and an
old image could silently reintroduce the old domain.

## Decision

1. **One input:** `global.baseDomain` in `helm/blitz-pay/values-<env>.yaml`. Hostnames are
   `<global.hosts.X>.<baseDomain>`; subdomain labels are fixed per environment.
2. **The backend chart derives** its ingress host, TLS secret name, `FRONTEND_ORIGIN`, Stripe
   Connect URLs, `KEYCLOAK_IAM_SERVER_URL` and `STORAGE_ENDPOINT` from it.
3. **The chart publishes** all hostnames in the `blitzpay-domains` ConfigMap. It is the only
   distribution channel to other deployables:
   - `k8s/apply-infra.sh` substitutes `${KEY}` placeholders in the Keycloak/MinIO ingresses;
   - the dashboard deploy workflow substitutes them in its ingress manifests;
   - the dashboard and storefront containers receive them as env vars (`envFrom`) and
     render `config.js` / the nginx proxy target at start.
4. **Images carry no hostnames.** The dashboard loads `config.js` before its bundle; the
   repo copy only serves local dev and tests.

## Consequences

- A domain change is one line plus redeploys (backend first, then infra and dashboard); see
  [`reference/domain-configuration.md`](../../reference/domain-configuration.md).
- The backend must deploy to a namespace before the dashboard can; the dashboard deploy
  fails fast if `blitzpay-domains` is missing.
- DNS, Keycloak client redirect URIs, AWS (CDK), oracle-dev and the mobile apps remain
  outside this mechanism and are updated separately.
- Explicit overrides (`ingress.hostname`, `config.frontendOrigin`, …) remain for environments
  without `baseDomain` (oracle-dev); setting them in staging or production bypasses the rule.
