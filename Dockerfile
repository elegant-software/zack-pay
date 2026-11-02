# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Pinned to the runner's native platform: the jar is platform-independent, so building it
# under QEMU for arm64 only multiplies build time (~10x) for an identical artifact
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and dependency manifests first for layer caching
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

# Copy source and build the fat jar
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/app.jar app.jar

EXPOSE 8080

# JVM defaults to 25% of the container limit (256MB on 1Gi) — too small, startup GC-thrashes.
# 70% leaves room for metaspace/threads/code cache. G1 + ActiveProcessorCount: with <2 CPUs the JVM
# silently picks SerialGC, which can thrash for hours without ever throwing OOM (staging, 2026-11-02).
# ExitOnOutOfMemoryError: crash fast and let k8s restart instead of hanging half-alive.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseG1GC", "-XX:ActiveProcessorCount=2", "-XX:+ExitOnOutOfMemoryError", "-Xlog:gc:stderr", "-jar", "app.jar"]
