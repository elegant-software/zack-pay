# Docker Best Practices

Authoritative reference for Docker conventions in this project.
See `CONTRIBUTING.md` for the link to this document.

---

## 1. Multi-Stage Builds (Required)

Always use a multi-stage Dockerfile for JVM applications. Never build the JAR outside
Docker and copy it in.

**Why:**
- Final image contains only the JRE + JAR — no build tools, no source code, smaller attack surface
- No dependency on a pre-built artifact; CD pipeline only needs source code
- Reproducible: JDK version and Gradle version are pinned in the Dockerfile, not the CI runner
- Runtime image is ~200MB instead of ~600MB (JRE vs full JDK)

**Pattern used in this project:**

```dockerfile
# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy dependency manifests first — these layers are cached until build files change
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

# Copy source and build — cache invalidates only when source changes
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 2. Layer Caching Order

Order `COPY` and `RUN` instructions from least to most frequently changed.
Each instruction creates a layer; a changed layer invalidates all layers below it.

```dockerfile
# 1. Dependency manifests — change rarely
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
RUN ./gradlew dependencies   ← cached until build files change

# 2. Source code — changes on every commit
COPY src/ src/
RUN ./gradlew bootJar        ← only re-runs when source changes
```

If you invert this (copy src first), every source change re-downloads all dependencies.

---

## 3. `.dockerignore`

Keep `build/` in `.dockerignore`. With multi-stage builds the JAR is always built inside
Docker — a pre-built `build/` directory in the context is not only unnecessary but can
cause stale artifact issues.

Current `.dockerignore` entries that matter:

```
build          ← correct — Dockerfile builds its own JAR
.gradle        ← correct — Gradle cache is internal to the build stage
*.pem          ← correct — private keys must never enter the image
*.key          ← correct
.env*          ← correct — environment secrets must never enter the image
```

**Never remove `build` from `.dockerignore`.** Doing so would silently let a local build
artifact shadow the Docker-built one, making image content dependent on local state.

---

## 4. Use JRE Not JDK for the Runtime Stage

The build stage needs the full JDK (compiler, tools). The runtime stage only needs the JRE.

```dockerfile
FROM eclipse-temurin:25-jdk AS build   # JDK for compiling
FROM eclipse-temurin:25-jre            # JRE for running (~200MB vs ~600MB)
```

Keep both stages pinned to the same major version as `javaVersion` in
`gradle.properties` / `JAVA_VERSION` GitHub variable.

---

## 5. Pass `-x test` in the Dockerfile Build Step

Tests run in CI (`ci-build.yml`) before the Docker image is built (`cd-build-image.yml`). Re-running tests
inside Docker adds build time without adding safety — CI already caught failures.

```dockerfile
RUN ./gradlew bootJar -x test --no-daemon -q
```

---

## 6. `--no-daemon` in Docker

Always pass `--no-daemon` to Gradle inside Docker. The Gradle daemon is designed for
long-running interactive sessions and wastes memory in a one-shot container build.

---

## References

- Docker multi-stage builds: https://docs.docker.com/build/building/multi-stage/
- eclipse-temurin images: https://hub.docker.com/_/eclipse-temurin
- Docker layer caching: https://docs.docker.com/build/cache/
