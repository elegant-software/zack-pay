# ADR-0015 — API authentication: JWT Bearer via Keycloak OAuth2 resource server

## Status
Accepted

## Context

No Spring Security dependency exists in the project. Every endpoint — business APIs, MCP server,
actuator, Swagger UI — is reachable without any authentication. Keycloak is already deployed as
the IAM server and issues JWTs to merchant users, shoppers, and admins. The goal is to ensure
no API surface is reachable without a valid token, with a minimal, auditable permit list for
callers that structurally cannot present a Keycloak JWT.

Several inbound callers cannot present a Keycloak JWT:
- **TrueLayer webhooks** (`/**/webhooks/truelayer`) — authenticated via TrueLayer signature
  verification (Nimbus JWT against TrueLayer's JKU); already enforced in the controller
- **Stripe webhooks** (`/**/webhooks/stripe`) — authenticated via Stripe webhook secret;
  already enforced in the controller
- **Stripe Connect return redirect** (`/stripe-connect/return`) — browser redirect from
  Stripe's hosted Connect onboarding; no token available at redirect time
- **K8s liveness/readiness probes** (`/actuator/health`) — cluster-internal HTTP with no
  credential injection
- **Prometheus scraper** (`/actuator/prometheus`) — restricted at the Kubernetes NetworkPolicy
  layer; not on the application permit list

The MCP server (`spring.ai.mcp.server.protocol: STREAMABLE`) is externally reachable and
must be treated the same as the business APIs.

## Decision

1. **Add `spring-boot-starter-oauth2-resource-server`** to the dependency set.

2. **Configure a single `SecurityFilterChain`** with:
   - JWT Bearer validation against one issuer:
     `${KEYCLOAK_IAM_SERVER_URL}/realms/${KEYCLOAK_IAM_REALM}`
   - `anyRequest().authenticated()` as the default rule
   - Permit list restricted to paths that cannot structurally carry a Keycloak token:
     - `/actuator/health`
     - `/**/webhooks/truelayer`
     - `/**/webhooks/stripe`
     - `/stripe-connect/return`

3. **Prometheus is NOT on the application permit list.** Restrict scraper access via
   Kubernetes NetworkPolicy so only the Prometheus pod can reach `/actuator/prometheus`.

4. **MCP server and Swagger UI / api-docs require a valid JWT.** No profile-based exception.

5. **Authorization (role enforcement) stays inside the service layer.** The filter chain
   enforces only "is this a valid Keycloak JWT?"; which roles can call which operations is
   owned by the Modulith service layer, not the security config.

6. **Remove `env` from actuator exposure in all profiles.** The `env` endpoint with
   `show-values: ALWAYS` exposes runtime secrets (database credentials, payment provider
   keys, object storage keys). It is removed from `management.endpoints.web.exposure.include`
   in every profile. If environment inspection is needed in an emergency, use `kubectl exec`
   or the cloud provider's secrets manager console.

## Alternatives considered

- **API keys instead of JWT** — rejected; Keycloak already issues JWTs to all callers,
  adding a parallel key scheme doubles the credential surface.
- **Multi-issuer resource server** — rejected; all callers (merchants, shoppers, admins,
  MCP clients) authenticate against one Keycloak realm.
- **Role gates in the filter chain** — rejected; duplicating role logic at the filter level
  and the service layer creates a maintenance trap when roles evolve.
- **Permitting Prometheus in the filter chain** — rejected; Prometheus pods are cluster-internal
  and NetworkPolicy is the right control boundary for cluster-internal scraping.
- **Permitting Swagger UI in non-production profiles** — rejected; any caller who needs to
  read the spec already has a token, and profile-conditional security rules are audit risk.
