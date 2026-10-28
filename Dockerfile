# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build

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

# JVM defaults to 25% of the container limit (256MB on 1Gi) — too small, startup GC-thrashes
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
