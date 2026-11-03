import org.springframework.boot.gradle.tasks.bundling.BootJar


plugins {
    alias(libs.plugins.skillsjars)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.dependency.track) apply false
    alias(libs.plugins.spring.cloud.contract)
    `maven-publish`
}

group = "com.elegant.software.blitzpay"
version = "0.2.2"
description = "BlitzPay"

// TODO: remove once Spring Boot BOM pins ByteBuddy >= 1.17 (supports Java 25 class files)
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "net.bytebuddy") useVersion("1.17.7")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt())
    }
}
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}


val mavenProxyUrl = providers.gradleProperty("mavenProxyUrl").orNull
val skillsJarsRepositoryUrl = providers.gradleProperty("skillsJarsRepositoryUrl").orNull
val sourceSets = the<org.gradle.api.tasks.SourceSetContainer>()
val contractTestSourceSet = sourceSets.maybeCreate("contractTest").apply {
    java.setSrcDirs(listOf("src/contractTest/kotlin"))
    resources.setSrcDirs(listOf("src/contractTest/resources"))
    compileClasspath += sourceSets["main"].output + configurations["testCompileClasspath"]
    runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
}

val jooqCodegenSourceSet = sourceSets.create("jooqCodegen")

repositories {
    mavenLocal()

    if (mavenProxyUrl != null) {
        maven { url = uri(mavenProxyUrl) }
    } else {
        mavenCentral()
    }

    maven { url = uri("https://repo.spring.io/snapshot") }

    if (skillsJarsRepositoryUrl != null) {
        maven { url = uri(skillsJarsRepositoryUrl) }
    }
}

dependencies {
    if (providers.gradleProperty("includeSkillsJars").map(String::toBoolean).orElse(false).get()) {
        // Optional: `com.skillsjars:jdubois__dr-jskill` runtime integration
        runtimeOnly(libs.dr.jskill)
    }
    implementation(libs.bootui.spring.boot.starter)
    // ----------------------------
    // Modulith: `invoice`
    // Purpose: ZUGFeRD/Factur-X invoice XML + Thymeleaf->PDF rendering
    // ----------------------------
    implementation(libs.mustang.library)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.flying.saucer.pdf)

    // ----------------------------
    // Modulith: `payments.stripe` + `payments.braintree`
    // Purpose: Stripe card payment + Braintree PayPal/digital wallet
    // ----------------------------
    implementation(libs.stripe.java)
    implementation(libs.braintree.java)

    // ----------------------------
    // AWS: Spring Cloud AWS (S3 + Secrets Manager)
    // ----------------------------
    implementation(libs.spring.cloud.aws.starter.s3)
    implementation(libs.spring.cloud.aws.starter.secrets.manager)

    // ----------------------------
    // Modulith: `payments` (TrueLayer + QRPay)
    // Purpose: payment webhooks/signatures + SSE payment updates
    // ----------------------------
    implementation(libs.truelayer.java)
    implementation(libs.truelayer.signing) // TrueLayer official signing lib
    implementation(libs.logstash.logback.encoder) // Structured JSON logging (production)


    // ----------------------------
    // Modulith: `merchant.iam` — User Management
    // Purpose: Keycloak Admin Client for user search and group membership management
    // ----------------------------
    implementation(libs.keycloak.admin.client)

    // ----------------------------
    // Shared platform (all Modulith modules)
    // Purpose: web/http, JSON, Modulith runtime, OpenAPI
    // ----------------------------
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.micrometer.tracing.opentelemetry)
    implementation(libs.aspectjweaver)
    runtimeOnly(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.opentelemetry.exporter.otlp)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // ----------------------------
    // Modulith: `merchant`
    // Purpose: Expose merchant product update as MCP
    // ----------------------------
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.springframework.ai:spring-ai-openai")
    // ----------------------------
    // Runtime support (used across modules)
    // ----------------------------
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // ----------------------------
    // jOOQ codegen (`./gradlew generateJooq`) — not part of the app
    // ----------------------------
    "jooqCodegenImplementation"("org.jooq:jooq-codegen")
    "jooqCodegenImplementation"("org.liquibase:liquibase-core")
    "jooqCodegenImplementation"("org.testcontainers:testcontainers-postgresql")
    "jooqCodegenRuntimeOnly"("org.postgresql:postgresql")
    "jooqCodegenRuntimeOnly"("org.slf4j:slf4j-simple:2.0.18")

    // ----------------------------
    // Tests (shared)
    // ----------------------------
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.kotlin)
}

// Generated jOOQ sources are committed under src/generated/jooq, so compiling (incl. the Docker build)
// needs no database. After changing a migration that touches a table used by jOOQ queries, run
// `./gradlew generateJooq` (requires Docker) and commit the result; CI fails if it is stale.
extra["jooq.version"] = libs.versions.jooq.get()
val generatedJooqDir = "src/generated/jooq"
sourceSets["main"].java.srcDir(generatedJooqDir)

tasks.register<JavaExec>("generateJooq") {
    group = "build"
    description = "Applies the Liquibase changelog to a PostgreSQL Testcontainer and regenerates jOOQ sources from it."
    classpath = jooqCodegenSourceSet.runtimeClasspath
    mainClass.set("com.elegant.software.blitzpay.jooqcodegen.JooqCodegenKt")
    args(file("src/main/resources").absolutePath, file(generatedJooqDir).absolutePath)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai.get()}")
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:${libs.versions.spring.cloud.aws.get()}")
    }
}

configurations.named("contractTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named("contractTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property", "-jvm-target=25")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
    // Disable Spring Cloud AWS auto-config in tests — no real AWS credentials in CI
    systemProperty("spring.cloud.aws.s3.enabled", "false")
    systemProperty("spring.cloud.aws.secretsmanager.enabled", "false")
    systemProperty("spring.cloud.aws.region.static", "us-east-1")
    // Each test class with its own @MockitoBean set boots a separate full context (Tomcat, Modulith...).
    // Gradle's default 512m test heap OOMs once ~7 are cached. ponytail: cap the cache instead of
    // per-test tuning; raise maxSize if context reloads start dominating test time.
    maxHeapSize = "1536m"
    systemProperty("spring.test.context.cache.maxSize", "3")
}

tasks.named<Test>("contractTest") {
    description = "Runs Boot 4-compatible contract tests."
    group = "verification"
    testClassesDirs = contractTestSourceSet.output.classesDirs
    classpath = contractTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("contractTest")
}



if (providers.gradleProperty("sbom").isPresent) {
    apply(plugin = libs.plugins.cyclonedx.get().pluginId)
    apply(plugin = libs.plugins.dependency.track.get().pluginId)

    tasks.named<org.cyclonedx.gradle.CyclonedxAggregateTask>("cyclonedxBom") {
        jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/bom.json"))
    }

    configure<com.liftric.dtcp.extensions.DepTrackCompanionExtension> {
        url.set(providers.gradleProperty("dependencyTrackUrl").orElse(System.getenv("DT_URL") ?: ""))
        apiKey.set(providers.gradleProperty("dependencyTrackApiKey").orElse(System.getenv("DT_API_KEY") ?: ""))
        projectName.set(project.name)
        projectVersion.set(project.version.toString())
        autoCreate.set(true)
    }
}

contracts {
    contractsDslDir.set(file("src/contractTest/resources/contracts"))
}

// We use handwritten WebTestClient contract tests; suppress SCC's generated test classes.
afterEvaluate {
    tasks.findByName("generateContractTests")?.enabled = false
}

publishing {
    publications {
        create<MavenPublication>("stubs") {
            artifact(tasks.named("verifierStubsJar"))
            groupId = "com.elegant.software.blitzpay"
            artifactId = "blitz-pay"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/elegant-software/blitz-pay")
            credentials {
                username = providers.gradleProperty("githubActor").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("githubToken").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
