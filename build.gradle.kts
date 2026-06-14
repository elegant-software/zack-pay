import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.jooq.meta.jaxb.Property


plugins {
    alias(libs.plugins.skillsjars)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.dependency.track)
    alias(libs.plugins.spring.cloud.contract)
    alias(libs.plugins.studer.jooq)
    `maven-publish`
}

group = "com.elegant.software.blitzpay"
version = "0.2.2"
description = "BlitzPay"

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
    implementation(libs.aws.s3)

    // ----------------------------
    // Modulith: `payments` (TrueLayer + QRPay)
    // Purpose: payment webhooks/signatures + SSE payment updates
    // ----------------------------
    implementation(libs.truelayer.java)
    implementation(libs.truelayer.signing) // TrueLayer official signing lib
    implementation(libs.nimbus.jose.jwt) // webhook signature verification
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

    jooqGenerator("org.jooq:jooq-meta-extensions:${libs.versions.jooq.get()}")
    jooqGenerator("org.slf4j:slf4j-simple:2.0.16")

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

jooq {
    version.set(libs.versions.jooq.get())

    // Minimal DDL for tables needed by the dashboard queries that live outside the
    // original reporting schema. Inline rather than including full migration files
    // to avoid PostgreSQL-specific syntax (RLS, ENABLE ROW LEVEL SECURITY, etc.)
    // that jOOQ's DDLDatabase cannot parse.
    val dashboardExtensionDdl = """
        CREATE TABLE blitzpay.merchant_applications (
            id              UUID        NOT NULL PRIMARY KEY,
            merchant_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
            created_at      TIMESTAMPTZ NOT NULL
        );
        CREATE TABLE blitzpay.merchant_branches (
            id                      UUID        NOT NULL PRIMARY KEY,
            merchant_application_id UUID        NOT NULL,
            active                  BOOLEAN     NOT NULL DEFAULT TRUE,
            created_at              TIMESTAMPTZ NOT NULL
        );
        CREATE TABLE blitzpay.appointments (
            id          UUID        NOT NULL PRIMARY KEY,
            merchant_id UUID        NOT NULL,
            branch_id   UUID        NOT NULL,
            status      VARCHAR(32) NOT NULL DEFAULT 'BOOKED',
            created_at  TIMESTAMPTZ NOT NULL
        );
        ALTER TABLE blitzpay.order_orders
            ADD COLUMN order_type VARCHAR(32) NOT NULL DEFAULT 'PRE_ORDER';
    """.trimIndent()

    val reportingSchemaSql = listOf(
        "20260417-001-create-blitzpay-schema.sql",
        "20260430-001-create-order-tables.sql",
        "20260610-001-payment-settlements.sql",
        "20260610-002-payment-settlement-reconciliation.sql",
    ).joinToString("\n") { file("src/main/resources/db/changelog/$it").readText() } +
        "\n" + dashboardExtensionDdl

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                generator.apply {
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties.addAll(listOf(
                            Property().withKey("sql").withValue(reportingSchemaSql),
                            Property().withKey("dialect").withValue("POSTGRES"),
                            Property().withKey("defaultNameCase").withValue("lower"),
                        ))
                        inputSchema = "blitzpay"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "com.elegant.software.blitzpay.jooq"
                        directory = "build/generated/jooq/main"
                    }
                }
            }
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai.get()}")
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



tasks.named<org.cyclonedx.gradle.CyclonedxAggregateTask>("cyclonedxBom") {
    jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/bom.json"))
}

dependencyTrackCompanion {
    url.set(providers.gradleProperty("dependencyTrackUrl").orElse(System.getenv("DT_URL") ?: ""))
    apiKey.set(providers.gradleProperty("dependencyTrackApiKey").orElse(System.getenv("DT_API_KEY") ?: ""))
    projectName.set(project.name)
    projectVersion.set(project.version.toString())
    autoCreate.set(true)
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
