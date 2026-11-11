package com.elegant.software.blitzpay.contract

import org.junit.jupiter.api.Test
import java.io.File
import java.time.Duration

class OpenApiGeneratorTest : ContractVerifierBase() {

    @Test
    fun `generate OpenAPI specification`() {
        val response = webTestClient.mutate()
            // The first request builds the complete schema, including multipart category variants.
            .responseTimeout(Duration.ofSeconds(60))
            .build()
            .get()
            .uri("/api-docs.yaml") // Springdoc provides YAML at .yaml suffix
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()

        val yamlContent = response.responseBody
        requireNotNull(yamlContent) { "OpenAPI content was null" }
        val normalizedYamlContent = yamlContent.replace(
            Regex("""(?m)^- url: http://localhost:\d+$"""),
            "- url: http://localhost:8080"
        )

        // Save to api-docs folder
        val outputDir = File("api-docs")
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val outputFile = File(outputDir, "api-doc.yml")
        outputFile.writeText(normalizedYamlContent)
        
        println("OpenAPI documentation updated at: ${outputFile.absolutePath}")
    }
}
