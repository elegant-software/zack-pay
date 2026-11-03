# Secret Setup Guide

This chart requires **two** Kubernetes Secrets to be created before installing.
Both are managed outside Helm and persist across deployments.

---

## Secret 1 — Application credentials (`blitz-pay-secrets`)

Injected as environment variables via `envFrom.secretRef`.

### Staging

```bash
kubectl create secret generic blitz-pay-secrets \
  --namespace blitz-pay-staging \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://<staging-host>:5432/quickpay_db" \
  --from-literal=SPRING_DATASOURCE_USERNAME="<staging-user>" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="<staging-password>" \
  --from-literal=TRUELAYER_CLIENT_ID="<sandbox-client-id>" \
  --from-literal=TRUELAYER_CLIENT_SECRET="<sandbox-client-secret>" \
  --from-literal=TRUELAYER_KEY_ID="<key-id>" \
  --from-literal=TRUELAYER_MERCHANT_ACCOUNT_ID="<merchant-id>" \
  --from-literal=TRUELAYER_ACCESS_TOKEN="<sandbox-access-token>" \
  --from-literal=KEYCLOAK_IAM_ADMIN_CLIENT_ID="<keycloak-admin-client-id>" \
  --from-literal=KEYCLOAK_IAM_ADMIN_CLIENT_SECRET="<keycloak-admin-client-secret>"
```

### Production

```bash
kubectl create secret generic blitz-pay-secrets \
  --namespace blitz-pay-prod \
  --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://<prod-host>:5432/quickpay_db" \
  --from-literal=SPRING_DATASOURCE_USERNAME="<prod-user>" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="<prod-password>" \
  --from-literal=TRUELAYER_CLIENT_ID="<prod-client-id>" \
  --from-literal=TRUELAYER_CLIENT_SECRET="<prod-client-secret>" \
  --from-literal=TRUELAYER_KEY_ID="<prod-key-id>" \
  --from-literal=TRUELAYER_MERCHANT_ACCOUNT_ID="<prod-merchant-id>" \
  --from-literal=TRUELAYER_ACCESS_TOKEN="<prod-access-token>" \
  --from-literal=KEYCLOAK_IAM_ADMIN_CLIENT_ID="<keycloak-admin-client-id>" \
  --from-literal=KEYCLOAK_IAM_ADMIN_CLIENT_SECRET="<keycloak-admin-client-secret>"
```

> **Keycloak**: `KEYCLOAK_IAM_ADMIN_CLIENT_*` is the service-account client in the
> `blitzpay` realm (realm-management roles `manage-users`, `query-groups`, and `manage-clients`
> for Kitchen Screen provisioning, see `reference/kitchen-screen-keycloak-provisioning.md`).
> Merchant and branch creation sync Keycloak groups and fail with 401 if these are missing. Add to an
> existing secret with:
>
> ```bash
> kubectl -n <namespace> patch secret blitz-pay-secrets --type merge -p \
>   '{"stringData":{"KEYCLOAK_IAM_ADMIN_CLIENT_ID":"<id>","KEYCLOAK_IAM_ADMIN_CLIENT_SECRET":"<secret>"}}'
> kubectl -n <namespace> rollout restart deploy/blitz-pay
> ```

> **Note**: `TRUELAYER_PRIVATE_KEY_PATH` is **not** in this secret. It is the
> in-container file path (non-sensitive) and is set automatically by the ConfigMap.

---

## Secret 2 — TrueLayer EC private key (`blitz-pay-truelayer-pem`)

The TrueLayer SDK reads the signing key from a file path — key material cannot be
passed as a plain env var string. This secret is mounted as a read-only file at
`/var/run/secrets/truelayer/private.pem` inside the container.

### Staging

```bash
kubectl create secret generic blitz-pay-truelayer-pem \
  --namespace blitz-pay-staging \
  --from-file=private.pem=./ec512-private-key.pem
```

### Production

```bash
kubectl create secret generic blitz-pay-truelayer-pem \
  --namespace blitz-pay-prod \
  --from-file=private.pem=./ec512-private-key.pem
```

The chart automatically sets `TRUELAYER_PRIVATE_KEY_PATH=/var/run/secrets/truelayer/private.pem`
in the ConfigMap — no manual configuration needed.

---

## Rotating a credential

Update a single key without recreating the whole secret:

```bash
kubectl patch secret blitz-pay-secrets \
  --namespace blitz-pay-prod \
  --type='json' \
  -p='[{"op":"replace","path":"/data/TRUELAYER_ACCESS_TOKEN","value":"'$(echo -n "<new-token>" | base64)'"}]'
```

Rotating the PEM key:

```bash
kubectl create secret generic blitz-pay-truelayer-pem \
  --namespace blitz-pay-prod \
  --from-file=private.pem=./new-ec512-private-key.pem \
  --dry-run=client -o yaml | kubectl apply -f -
```

Then trigger a rolling restart to pick up the new value:

```bash
kubectl rollout restart deployment/blitz-pay -n blitz-pay-prod
```

---

## Future: Migrating to AWS Secrets Manager

Both secrets are designed for zero-change migration via
[External Secrets Operator (ESO)](https://external-secrets.io/):

1. Install ESO Helm chart in your cluster
2. Create an AWS IAM role for ESO pods (IRSA on EKS)
3. Create a `SecretStore` pointing to your AWS region
4. Create two `ExternalSecret` resources:

```yaml
# Syncs API credentials → blitz-pay-secrets
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: blitz-pay-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: SecretStore
  target:
    name: blitz-pay-secrets
  dataFrom:
    - extract:
        key: blitz-pay/prod/credentials
---
# Syncs PEM file content → blitz-pay-truelayer-pem
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: blitz-pay-truelayer-pem
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: SecretStore
  target:
    name: blitz-pay-truelayer-pem
  data:
    - secretKey: private.pem
      remoteRef:
        key: blitz-pay/prod/truelayer-private-key
```

ESO creates and syncs both K8s Secrets automatically. **No changes to the Helm
chart templates or values files are required.**