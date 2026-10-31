package com.elegant.software.blitzpay.storage

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import kotlin.test.assertEquals

class StorageConfigTest {

    private val publicEndpoint = URI.create("https://minio.example.test")

    @Test
    fun `internal endpoint overrides the shared S3 endpoint for the client`() {
        assertEquals(URI.create("http://minio:9000"), clientEndpoint(internalEndpoint = "http://minio:9000"))
    }

    @Test
    fun `blank internal endpoint keeps the shared S3 endpoint`() {
        assertEquals(publicEndpoint, clientEndpoint(internalEndpoint = ""))
    }

    private fun clientEndpoint(internalEndpoint: String?): URI {
        val builder = S3Client.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .endpointOverride(publicEndpoint)

        StorageConfig()
            .internalEndpointS3ClientCustomizer(StorageProperties(bucket = "b", internalEndpoint = internalEndpoint))
            .customize(builder)

        return builder.build().use { it.serviceClientConfiguration().endpointOverride().orElseThrow() }
    }
}
