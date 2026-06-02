package com.elegant.software.blitzpay.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.web.servlet.config.annotation.CorsRegistry

class WebFluxCorsConfigTest {

    private val corsProperties = CorsProperties()
    private val config = WebMvcVersioningConfig(ApiVersionProperties(), corsProperties)

    @Test
    fun `registers angular dev cors mapping globally`() {
        val registry = TestCorsRegistry()

        config.addCorsMappings(registry)

        val corsConfiguration = registry.corsConfigurations()["/**"]
        assertNotNull(corsConfiguration)
        assertEquals(corsProperties.allowedOrigins, corsConfiguration.allowedOrigins)
        assertTrue(corsConfiguration.allowedMethods.orEmpty().containsAll(corsProperties.allowedMethods))
        assertEquals(corsProperties.allowedHeaders, corsConfiguration.allowedHeaders)
    }

    private class TestCorsRegistry : CorsRegistry() {
        fun corsConfigurations() = getCorsConfigurations()
    }
}
