package com.elegant.software.blitzpay.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebFluxVersioningConfigTest {

    private val resolver = PathOnlyApiVersionResolver()

    @Test
    fun `extracts version from v-prefixed api path`() {
        val request = MockHttpServletRequest("GET", "/v1/invoices")
        assertEquals("1", resolver.resolveVersion(request))
    }

    @Test
    fun `supports semantic versions in api path`() {
        val request = MockHttpServletRequest("GET", "/v1.2/payments/request")
        assertEquals("1.2", resolver.resolveVersion(request))
    }

    @Test
    fun `ignores non api paths`() {
        val request = MockHttpServletRequest("GET", "/swagger-ui/index.html")
        assertNull(resolver.resolveVersion(request))
    }

    @Test
    fun `ignores mcp paths`() {
        val request = MockHttpServletRequest("GET", "/mcp/sse")
        assertNull(resolver.resolveVersion(request))
    }
}
