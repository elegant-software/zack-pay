package com.elegant.software.blitzpay.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.web.filter.OncePerRequestFilter

/** Authenticates `Authorization: Bearer kt_...` Kiosk tokens; every other request passes through untouched. */
class KioskTokenAuthenticationFilter(private val verifier: KioskTokenVerifier) : OncePerRequestFilter() {
    private val resolver = DefaultBearerTokenResolver()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = resolver.resolve(request)
        if (token == null || !token.startsWith(KIOSK_TOKEN_PREFIX)) {
            chain.doFilter(request, response)
            return
        }
        val principal = verifier.verify(token)
        if (principal == null) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = KioskAuthentication(principal)
        SecurityContextHolder.setContext(context)
        try {
            chain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    companion object {
        /** Keeps the Keycloak JWT machinery away from Kiosk tokens. */
        val jwtBearerResolver = BearerTokenResolver { request ->
            DefaultBearerTokenResolver().resolve(request)?.takeUnless { it.startsWith(KIOSK_TOKEN_PREFIX) }
        }
    }
}
