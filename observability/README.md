# BlitzPay Observability

BlitzPay emits Micrometer/OpenTelemetry traces over OTLP/HTTP, Prometheus
metrics at `/actuator/prometheus`, and structured logs containing `requestId`,
`traceId`, and `spanId`.

## Ownership

This repository owns application instrumentation, telemetry conventions, and
portable Grafana/collector definitions. The infrastructure repository owns
deployment of the OpenTelemetry Collector, Tempo, Loki, Prometheus, Grafana,
and datasource credentials.

## Collector

Apply `collector-tail-sampling.yaml` to the platform collector configuration.
It expects Tempo's OTLP endpoint at `tempo:4317`; override that endpoint in the
infrastructure repository. The application sends every span to the collector.

## Grafana

- Import `grafana/blitz-pay-apm-dashboard.json`.
- Provision `grafana/alert-rules.yaml` after replacing datasource UID
  `prometheus`.
- Merge `grafana/datasources.yaml` into Grafana provisioning and replace the
  datasource URLs when service discovery differs.

## ECS

Set these environment variables on the Fargate task:

```text
MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=true
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://<collector>:4318/v1/traces
OTEL_EXPORTER_OTLP_TRACES_TRANSPORT=http
OTEL_SERVICE_NAME=blitz-pay
OTEL_SERVICE_VERSION=<image-tag>
OTEL_DEPLOYMENT_ENVIRONMENT=prod
OTEL_CLOUD_PLATFORM=aws_ecs
```

Keep export disabled for local development and automated tests unless a
collector endpoint is deliberately supplied.

## Data policy

Spans may contain route templates, methods, status, duration, error type,
provider/operation names, and explicitly allowlisted business references.
Request/response bodies, authorization data, customer identity, contact data,
SQL text, bind values, and arbitrary query parameters must never be attached.
