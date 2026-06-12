package com.elegant.software.blitzpay.config

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestIdWebFilterTest {
    private val filter = RequestIdWebFilter()

    @Test
    fun `preserves incoming request id and clears MDC`() {
        val request = MockHttpServletRequest().apply {
            addHeader(RequestIdWebFilter.REQUEST_ID_HEADER, "request-123")
        }
        val response = MockHttpServletResponse()
        var requestIdInChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            requestIdInChain = MDC.get(LogContext.REQUEST_ID)
        })

        assertEquals("request-123", requestIdInChain)
        assertEquals("request-123", response.getHeader(RequestIdWebFilter.REQUEST_ID_HEADER))
        assertNull(MDC.get(LogContext.REQUEST_ID))
    }

    @Test
    fun `generates request id when header is absent`() {
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest(), response, FilterChain { _, _ -> })

        assertNotNull(response.getHeader(RequestIdWebFilter.REQUEST_ID_HEADER))
    }
}
