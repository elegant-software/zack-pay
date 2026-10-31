{{/*
Expand the name of the chart.
*/}}
{{- define "blitz-pay.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "blitz-pay.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart label value (name-version).
*/}}
{{- define "blitz-pay.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to all resources.
*/}}
{{- define "blitz-pay.labels" -}}
helm.sh/chart: {{ include "blitz-pay.chart" . }}
{{ include "blitz-pay.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels — used for Service selectors and Deployment matchLabels.
*/}}
{{- define "blitz-pay.selectorLabels" -}}
app.kubernetes.io/name: {{ include "blitz-pay.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Public hostname for a service: "<global.hosts.NAME>.<global.baseDomain>".
Usage: {{ include "blitz-pay.host" (list . "api") }}
*/}}
{{- define "blitz-pay.host" -}}
{{- $root := index . 0 -}}
{{- printf "%s.%s" (index $root.Values.global.hosts (index . 1)) $root.Values.global.baseDomain -}}
{{- end }}

{{/*
TLS secret shared by every ingress on the base domain, e.g. zackpay.de -> wildcard-zackpay-de-tls.
*/}}
{{- define "blitz-pay.tlsSecretName" -}}
{{- .Values.ingress.tlsSecretName | default (printf "wildcard-%s-tls" (.Values.global.baseDomain | replace "." "-")) -}}
{{- end }}
