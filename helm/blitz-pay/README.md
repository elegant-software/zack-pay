# BlitzPay Helm Chart

Helm chart for deploying the BlitzPay Spring Boot application to Kubernetes.

## Prerequisites

- Kubernetes 1.28+
- Helm 3.x
- Nginx Ingress Controller installed in the cluster
- cert-manager installed (for TLS certificate provisioning)
- The `blitzpay-secrets` Kubernetes Secret created in the target namespace (see [SECRET-SETUP.md](./SECRET-SETUP.md))
- An OpenTelemetry Collector accepting OTLP/HTTP and forwarding traces to Tempo

## Tracing

The chart exports 100% of application spans to the collector configured by
`config.otlpTracesEndpoint`. The collector owns tail sampling and should retain
all errors, all traces slower than one second, and 10% of normal successful
traces. Disable export with `--set config.tracingEnabled=false`.

Grafana, Tempo, Loki, collector, dashboard, and alert provisioning references
live under `observability/`.

## Create the Secret First

The application will not start without the pre-created secret. See [SECRET-SETUP.md](./SECRET-SETUP.md) for the full `kubectl create secret` command.

## Deploying

### Staging

```bash
helm upgrade --install blitzpay ./helm/blitzpay \
  --namespace blitzpay-staging \
  --create-namespace \
  --values ./helm/blitzpay/values.yaml \
  --values ./helm/blitzpay/values-staging.yaml \
  --set image.tag=<image-tag> \
  --wait \
  --timeout 5m
```

### Production

```bash
helm upgrade --install blitzpay ./helm/blitzpay \
  --namespace blitzpay-prod \
  --create-namespace \
  --values ./helm/blitzpay/values.yaml \
  --values ./helm/blitzpay/values-prod.yaml \
  --set image.tag=<image-tag> \
  --wait \
  --timeout 5m
```

## CD Pipeline (Automated)

The `.github/workflows/cd.yml` workflow automatically deploys to production when a GitHub release is published. It requires a `KUBECONFIG` secret in the GitHub repository settings:

```bash
# Encode your kubeconfig and add it as the KUBECONFIG Actions secret
cat ~/.kube/config | base64
```

## Rolling Back

```bash
helm rollback blitzpay -n blitzpay-prod
```

## Validating the Chart

```bash
helm lint ./helm/blitzpay -f ./helm/blitzpay/values.yaml -f ./helm/blitzpay/values-prod.yaml
helm template blitzpay ./helm/blitzpay -f ./helm/blitzpay/values.yaml -f ./helm/blitzpay/values-prod.yaml --set image.tag=v0.2.2
```

## Full Deployment Guide

See [specs/005-k8s-helm-deploy/quickstart.md](../../specs/005-k8s-helm-deploy/quickstart.md).
