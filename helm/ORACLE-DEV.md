# oracle-dev — OKE on OCI Always Free (Ampere A1)

Isolated profile: staging/prod Helm values and Spring profiles are untouched.

| Piece | File |
|---|---|
| Backend Helm overrides (standalone, not layered on staging/prod) | `blitz-pay/values-oracle-dev.yaml` |
| Postgres + Keycloak overrides | `blitzpay-platform/values-oracle-dev.yaml` |
| Spring profile `oracle-dev` | `../src/main/resources/application-oracle-dev.yml` |

## Local kubectl setup (macOS)

OKE kubeconfigs call the `oci` CLI on every `kubectl` request to mint a short-lived token, so the CLI must be installed locally.

1. Install the CLI. Use pipx: `brew install oci-cli` compiles dependencies from source and is slow.
   ```bash
   brew install pipx && pipx install oci-cli && pipx ensurepath && exec zsh
   oci --version
   ```
2. Configure it with `oci setup config`:

   | Prompt | Value |
   |---|---|
   | User OCID | Console → profile icon → My profile → OCID → Copy |
   | Tenancy OCID | Console → profile icon → Tenancy: \<name\> → OCID → Copy |
   | Region | Home region ID shown top-right in the console, e.g. `eu-frankfurt-1` |
   | Generate new key pair | `Y`; keep the default directory `~/.oci` |

3. Upload the public key: `cat ~/.oci/oci_api_key_public.pem`, then Console → My profile → API keys → Add API key → Paste public key.
4. Test it: `oci iam region list --output table`. If you get `NotAuthenticated`, either the key was not uploaded (step 3), or the region or OCIDs in `~/.oci/config` are wrong.
5. Write a **separate** kubeconfig so the existing staging `~/.kube/config` is untouched. Copy the cluster OCID from Cluster → Access Cluster → Local Access:
   ```bash
   oci ce cluster create-kubeconfig --cluster-id <cluster-ocid> --region <region> \
     --token-version 2.0.0 --kube-endpoint PUBLIC_ENDPOINT \
     --file ~/.kube/oke-oracle-dev
   ```
6. Use it:
   ```bash
   export KUBECONFIG=~/.kube/oke-oracle-dev   # this shell only; unset KUBECONFIG to go back to staging
   kubectl get nodes -L kubernetes.io/arch    # expect 1 node, arm64
   ```
   pipx installs `oci` to `~/.local/bin`. Tools that don't load your shell PATH (for example IDE plugins) need that directory on their PATH, or the kubeconfig's `command: oci` changed to `/Users/<you>/.local/bin/oci`.

No local tools? Use OCI Cloud Shell (console `>_` icon). It is free and has `oci`, `kubectl` and `helm`. It can only reach clusters with a public endpoint, and local files have to be uploaded or `git clone`d there.

## Public access: Cloudflare Tunnel

No OCI LoadBalancer, no public IP, no ingress-nginx, no cert-manager: a `cloudflared` pod (blitzpay-platform chart)
opens an outbound tunnel and Cloudflare terminates TLS. Nothing here is billable on OCI.

| Public hostname | Tunnel service URL |
|---|---|
| `oc-api.zackpay.de` | `http://blitzpay-blitz-pay.bonami-dev.svc.cluster.local:9000` (helm release `blitzpay`) |
| `oc-auth.zackpay.de` | `http://keycloak.bonami-dev.svc.cluster.local:8080` (issuer in `application-oracle-dev.yml`) |
| `oc-admin.zackpay.de` | admin dashboard Service (its own repo, `k8s/`); CORS `frontendOrigin` |

One-time setup (Cloudflare dashboard → Zero Trust → Networks → Tunnels):

1. Create tunnel → Cloudflared → name `oracle-dev`. Copy the token from the install command (`--token <token>`);
   it goes into `blitzpay-platform-secrets` as `CLOUDFLARE_TUNNEL_TOKEN`.
2. Public Hostnames tab → add the three rows above (type `HTTP`). Cloudflare creates the proxied DNS CNAMEs itself.
3. After `helm upgrade --install platform ...` the tunnel shows **Healthy**.

All of it runs in namespace `bonami-dev`. Cloudflare only forwards the request through the tunnel; the service URL is
resolved by `cloudflared` inside the cluster via cluster DNS, so the full `<service>.<namespace>.svc.cluster.local`
name works from any namespace.

## Install

1. Secrets: `./helm/oracle-dev-secrets.sh` prompts for every value and writes all five secrets
   (`blitzpay-platform-secrets`, `blitz-pay-secrets`, `blitz-pay-truelayer-pem`, `minio-credentials`, `ghcr-pull-secret`)
   into `bonami-dev`, always via `~/.kube/oke-oracle-dev`. Re-run it any time; Enter keeps the stored value.
   The DB URL/user/password in `blitz-pay-secrets` are derived from the Postgres password, so they always match.
2. Charts:

```bash
export KUBECONFIG=~/.kube/oke-oracle-dev
helm upgrade --install platform ./helm/blitzpay-platform -n bonami-dev \
  -f ./helm/blitzpay-platform/values.yaml -f ./helm/blitzpay-platform/values-oracle-dev.yaml --wait --timeout 10m
helm upgrade --install blitzpay ./helm/blitz-pay -n bonami-dev \
  -f ./helm/blitz-pay/values.yaml -f ./helm/blitz-pay/values-oracle-dev.yaml --set image.tag=<image-tag>
```

## Staying inside the free tier

The free budget is 2 OCPU / 12 GB of A1 for the whole tenancy (1,500 OCPU-h + 9,000 GB-h per month).
The money guard is the cluster setup, not the charts:

- **Basic** cluster (Enhanced has a fee); one node pool, `VM.Standard.A1.Flex`, 2 OCPU / 12 GB total, autoscaler off.
- Storage: 200 GB free incl. boot volumes (50 GB/node). OCI block-volume PVCs are min 50 GB each.
- No `LoadBalancer` Services: public traffic goes through the Cloudflare Tunnel (above). A LoadBalancer Service
  without the flexible 10 Mbps shape annotations provisions a paid LB.
- Images must include `linux/arm64`.

## Pod budget

One 2 OCPU / 12 GB node (allocatable ≈ 1.8 CPU / 10.5 GB after kubelet/OS):

| Pod | CPU req / limit | Memory req / limit |
|---|---|---|
| blitz-pay | 250m / 1000m | 1Gi / 1.5Gi |
| admin dashboard (its own repo, `k8s/`) | 50m / 200m | 128Mi / 256Mi |
| keycloak | 250m / 1000m | 768Mi / 1Gi |
| postgres (50 GB volume) | 100m / 500m | 256Mi / 1Gi |
| cloudflared | 20m / 200m | 64Mi / 128Mi |
| OKE system | ≈ 230m | ≈ 500Mi |
| **Total requests** | **≈ 0.9 CPU** | **≈ 2.8 GB** |

CPU is the tight resource; memory has room. Storage: 50 GB boot + 50 GB Postgres = 100 of 200 GB free.
