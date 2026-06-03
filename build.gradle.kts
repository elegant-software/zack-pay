import java.net.Socket
import org.springframework.boot.gradle.tasks.bundling.BootJar


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

    // ----------------------------
    // AWS: Spring Cloud AWS (S3 + Secrets Manager) + Arconia dev services
    // ----------------------------
    implementation(libs.spring.cloud.aws.starter.s3)
    implementation(libs.spring.cloud.aws.starter.secrets.manager)
    developmentOnly(libs.arconia.dev.services.postgresql)
    developmentOnly(libs.arconia.dev.services.floci)

    // ----------------------------
    // Modulith: `payments` (TrueLayer + QRPay)
    // Purpose: payment webhooks/signatures + SSE payment updates
    // ----------------------------
    implementation(libs.truelayer.java)
    implementation(libs.truelayer.signing) // TrueLayer official signing lib
    implementation(libs.nimbus.jose.jwt) // webhook signature verification
    implementation(libs.logstash.logback.encoder) // Structured JSON logging (production)


    // ----------------------------
    // Shared platform (all Modulith modules)
    // Purpose: web/http, JSON, Modulith runtime, OpenAPI
    // ----------------------------
    implementation(libs.spring.boot.starter.actuator)
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
    testImplementation(libs.testcontainers.floci)
    testImplementation(libs.spring.boot.testcontainers.floci)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai.get()}")
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:${libs.versions.spring.cloud.aws.get()}")
        mavenBom("io.arconia:arconia-bom:${libs.versions.arconia.get()}")
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
    // Disable Secrets Manager and config import for all tests.
    // Integration tests using FlociTestConfig override these via @SpringBootTest(properties=[...]).
    systemProperty("spring.config.import", "")
    systemProperty("spring.cloud.aws.secretsmanager.enabled", "false")
    systemProperty("spring.cloud.aws.s3.enabled", "false")
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

tasks.register<Exec>("dockerTunnel") {
    group = "docker"
    description = "Ensures SSH tunnel to myserver Docker socket is active"
    onlyIf {
        try { Socket("localhost", 2375).close(); false }
        catch (_: Exception) { true }
    }
    commandLine("ssh", "-NfL", "localhost:2375:/var/run/docker.sock",
        "-L", "4566:localhost:4566",
        "-L", "5433:localhost:5433",
        "mehdi@myserver")
}

tasks.named("bootRun") {
    dependsOn("dockerTunnel")
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
