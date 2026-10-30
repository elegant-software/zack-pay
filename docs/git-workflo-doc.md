# GitHub Actions Workflows

All workflows in this project are **thin delegators** — they define triggers and permissions locally, then call reusable workflows from the shared pipeline library at [`elegant-software/elegant-ci-cd-pipeline`](https://github.com/elegant-software/elegant-ci-cd-pipeline) pinned to `@main`.

> **For agents:** Do not add inline build/test/deploy logic here. Any change to what CI/CD _does_ belongs in `elegant-ci-cd-pipeline`. Changes here are limited to triggers, permissions, inputs, and secrets forwarding.

---

## Workflow Overview

| File | Trigger | Purpose |
|---|---|---|
| `ci-build.yml` | `push`/`PR` → `main`, `develop` | Run tests + Qodana + SBOM upload |
| `cd-build-image.yml` | GitHub Release published/pre-released | Build Docker image and push to registry |
| `cd-deploy-k8s.yml` | `workflow_dispatch` | Deploy a specific image tag to staging or production via Helm |
| `pr-auto-label.yml` | PR opened/synchronize/reopened | Auto-apply labels based on `.github/labels.yml` |
| `release-notes.yml` | PR merged into `main` | Bump semver, generate release notes, create GitHub Release |
| `cleanup-releases-tags.yml` | Daily `02:15` + `workflow_dispatch` | Delete old releases and tags |
| `cleanup-workflow-runs.yml` | Daily `02:15` + `workflow_dispatch` | Delete old/failed workflow runs |

---

## Workflows in Detail

### CI · Build & Test (`ci-build.yml`)

**Triggers:** `push` and `pull_request` on `main` and `develop`. Also callable via `workflow_call` (used by `cd-build-image.yml`).

**Concurrency:** Cancels in-progress runs for the same branch/PR on new pushes.

**Jobs (in order):**
1. `test` — delegates to `backend-gradle-test.yml`; runs `./gradlew test` (unit tests only; integration test task set to `help` = no-op).
2. `qodana` — runs after `test`; delegates to `backend-qa-qodana-code-quality.yml`; posts results to PR checks.
3. `sbom-upload` — runs after `test`; uploads SBOM to Dependency-Track via `backnd-qa-dependency-track-upload.yml`.

**Repository variable used:** `JAVA_VERSION` (`vars.JAVA_VERSION`) — set this at the repo level to control the JDK.

---

### CD · Build Image (GHCR) (`cd-build-image.yml`)

**Trigger:** GitHub Release events — `published` or `prereleased`. This means a release must first be created by `release-notes.yml` before a Docker image is built.

**Jobs:**
1. `build` — calls `ci-build.yml` locally (runs full test suite).
2. `release` — delegates to `backend-build.yml`; builds and pushes the Docker image to the configured registry.

**Key inputs forwarded:**
- `image-name`: `${{ vars.APP_NAME }}` (e.g. `blitz-pay`)
- `image-tag`: `${{ github.ref_name }}` (the release tag, e.g. `v1.2.3`)
- `registry`: `${{ vars.CONTAINER_REGISTRY }}`

**Required repository variables:** `APP_NAME` and `CONTAINER_REGISTRY` — e.g. `blitz-pay` and `ghcr.io/elegant-software`.

---

### CD · Deploy to Kubernetes (`cd-deploy-k8s.yml`)

**Trigger:** Manual only (`workflow_dispatch`).

**Inputs:**
| Input | Required | Description |
|---|---|---|
| `image-tag` | Yes | Docker image tag to deploy (e.g. `v0.2.2`) |
| `environment` | Yes | `staging` or `production` |

**Runner:** Self-hosted — requires network access to the Kubernetes API server (`https://myserver:6443`) and pre-installed `helm` + `kubectl`.

**What it does:**
1. Verifies cluster connectivity.
2. Runs `helm upgrade --install $APP_NAME ./helm/$APP_NAME` with environment-specific values files:
   - `values.yaml` (base)
   - `values-staging.yaml` or `values-prod.yaml`
3. Sets `image.tag` to the provided input.
4. Waits up to 5 minutes for rollout, then verifies with `kubectl rollout status`.

**Namespaces:** `blitzpay-staging` / `blitzpay-prod`

**Note:** Kubeconfig is pre-configured on the self-hosted runner — no secret needed.

---

### PR · Auto-Label (`pr-auto-label.yml`)

**Trigger:** PR opened, synchronize, or reopened (any branch).

**Delegates to:** `pull-request-auto-label.yml` in the shared pipeline.

**Label configuration:** `.github/labels.yml` — edit this file to add/change labels and their matching rules. No workflow changes needed.

**Labels applied and their semver bump impact:**

| Label | Bump | Trigger |
|---|---|---|
| `breaking-change` | MAJOR | PR title/body contains `BREAKING CHANGE` or `!:` |
| `feature` | MINOR | Files changed under `src/main/kotlin/**` |
| `bug-fix` | PATCH | PR title or branch contains `fix`, `bug`, `hotfix` |
| `documentation` | PATCH | `*.md` files or `docs/**` |
| `infrastructure` | PATCH | `Dockerfile`, `docker-compose.yml`, `k8s/**`, `.github/**` |
| `dependencies` | PATCH | `build.gradle.kts`, `settings.gradle.kts`, `gradle/**` |
| `tests` | PATCH | `src/test/**` |
| `config` | PATCH | `src/main/resources/**` |

Multiple labels are applied when multiple patterns match.

---

### Release · Notes (`release-notes.yml`)

**Trigger:** PR merged (closed + `merged == true`) into `main`.

**Delegates to:** `release-notes.yml` in the shared pipeline.

**What it does (inside the shared pipeline):**
1. Reads labels from the merged PR.
2. Runs `semver.sh` to compute the next version (MAJOR > MINOR > PATCH priority; defaults to `0.1.0` / `0.0.1` when no prior tags exist).
3. Generates Markdown release notes grouped by label category.
4. Updates `version` in `build.gradle.kts` and pushes a `chore: release vX.Y.Z [skip ci]` commit.
5. Creates an annotated Git tag (`vX.Y.Z`).
6. Creates a GitHub Release — this in turn triggers `cd-build-image.yml` to build and push the Docker image.

> **Important for agents:** The release flow is fully automated. Never manually create Git tags or GitHub Releases unless recovering from a broken pipeline run. Doing so can produce duplicate tags or skip the `build.gradle.kts` version bump.

---

### Cleanup · Releases & Tags (`cleanup-releases-tags.yml`)

**Trigger:** Daily at `02:15 UTC` and `workflow_dispatch`.

**Default behavior:** Dry-run only (`dry_run: true`). To actually delete, trigger manually with `dry_run: false`.

**Deletes:** Releases and tags older than `older_than_days` (default: 30). Includes drafts and pre-releases.

---

### Cleanup · Workflow Runs (`cleanup-workflow-runs.yml`)

**Trigger:** Daily at `02:15 UTC` and `workflow_dispatch`.

**Default behavior:** Dry-run only (`dry_run: true`).

**Deletes:** Old and failed workflow run records older than `older_than_days` (default: 14).

---

## Status Badges

Badges in `README.md` use `?event=` or `?branch=` to target the correct run history. Using the wrong parameter returns a stale or broken badge.

| Workflow | Badge param | Reason |
|---|---|---|
| `ci-build.yml` | `?branch=main` | Push-triggered; scope to main for production signal |
| `cd-build-image.yml` | `?branch=main` | Release-triggered; last run always on main |
| `cd-deploy-k8s.yml` | `?event=workflow_dispatch` | Manual only; no branch context |
| `pr-auto-label.yml` | `?event=pull_request` | PR-only trigger |
| `release-notes.yml` | `?event=pull_request` | PR-only trigger (merged PRs) |

Cleanup workflows are intentionally not badged — they add noise without contributor signal.

---

## Required Repository Variables & Secrets

| Name | Type | Used by | Description |
|---|---|---|---|
| `APP_NAME` | Variable | `cd-build-image.yml`, `cd-deploy-k8s.yml` | Application name — must match `rootProject.name` in `settings.gradle.kts` |
| `JAVA_VERSION` | Variable | `ci-build.yml`, `cd-build-image.yml` | JDK version for Gradle builds |
| `CONTAINER_REGISTRY` | Variable | `cd-build-image.yml` | Registry host + org, e.g. `ghcr.io/elegant-software` |
| `OTLP_LOGS_ENDPOINT` | Variable (Environment-level) | `cd-deploy-k8s.yml` | OTLP logs endpoint injected into Helm ConfigMap as `OTLP_LOGS_ENDPOINT` |
| `QODANA_TOKEN` | Secret | `ci-build.yml` | Qodana Cloud token for code quality reports |
| `DTRACK_URL` | Secret | `ci-build.yml` | Dependency-Track base URL (optional) |
| `DTRACK_API_KEY` | Secret | `ci-build.yml` | Dependency-Track API key (optional) |

To set all of these at once, run:
```bash
.github/scripts/setup-github-env.sh
```
