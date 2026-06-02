package com.elegant.software.blitzpay.merchant.web

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class MerchantTenantFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val merchantId = extractMerchantId(req.requestURI)
        if (merchantId != null) {
            MerchantTenantContext.set(merchantId)
        }
        try {
            chain.doFilter(request, response)
        } finally {
            MerchantTenantContext.clear()
        }
    }

    private fun extractMerchantId(uri: String): UUID? {
        val segments = uri.split("/").filter { it.isNotEmpty() }
        val merchantsIndex = segments.indexOfFirst { it == "merchants" }
        if (merchantsIndex < 0 || merchantsIndex + 1 >= segments.size) return null
        return runCatching { UUID.fromString(segments[merchantsIndex + 1]) }.getOrNull()
    }
}
