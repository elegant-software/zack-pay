package com.elegant.software.blitzpay.storage

import io.awspring.cloud.autoconfigure.core.RegionProperties
import io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.env.MockEnvironment
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.assertEquals

class LocalAwsRegionTest {
    @Test
    fun `local S3 client has a region without dotenv system properties or AWS metadata`() {
        assertClientRegion(MockEnvironment(), "eu-central-1")
    }

    @Test
    fun `AWS region environment setting overrides the local fallback`() {
        assertClientRegion(MockEnvironment().withProperty("AWS_REGION", "eu-west-1"), "eu-west-1")
    }

    @Test
    fun `explicit Spring region setting overrides the local fallback`() {
        assertClientRegion(MockEnvironment().withProperty("spring.cloud.aws.region.static", "us-east-1"), "us-east-1")
    }

    private fun assertClientRegion(environment: MockEnvironment, expected: String) {
        YamlPropertySourceLoader().load("local", ClassPathResource("application-local.yml"))
            .forEach { environment.propertySources.addLast(it) }
        val properties = Binder.get(environment).bind("spring.cloud.aws.region", RegionProperties::class.java)
            .orElse(null) ?: RegionProperties()
        assertEquals(expected, properties.getStatic())
        val region = RegionProviderAutoConfiguration.createRegionProvider(properties).region
        S3Client.builder().region(region).credentialsProvider(AnonymousCredentialsProvider.create()).build().use {
            assertEquals(Region.of(expected), it.serviceClientConfiguration().region())
        }
    }
}
