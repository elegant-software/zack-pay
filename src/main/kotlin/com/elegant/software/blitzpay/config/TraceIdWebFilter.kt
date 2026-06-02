package com.elegant.software.blitzpay.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Assigns a per-request correlation id (`traceId`) and exposes it via:
 *  - SLF4J MDC for structured logging
 *  - Response header `X-Request-Id`
 *
 * Honours an incoming `X-Request-Id` header so upstream ids (TrueLayer webhook
 * id, load balancer ids, etc.) are preserved.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdWebFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse

        val incoming = req.getHeader(REQUEST_ID_HEADER) ?: req.getHeader("x-tl-webhook-id")
        val traceId = incoming?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        res.setHeader(REQUEST_ID_HEADER, traceId)

        MDC.put(TRACE_ID_KEY, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
