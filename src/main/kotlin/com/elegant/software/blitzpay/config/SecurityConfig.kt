package com.elegant.software.blitzpay.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuerUri: String,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .cors(Customizer.withDefaults())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // K8s liveness/readiness probes — cannot carry tokens
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    // Payment provider webhooks — signature-verified in the controller
                    .requestMatchers("/*/webhooks/**").permitAll()
                    // Stripe Connect OAuth return redirect — browser redirect, no token available
                    .requestMatchers("/stripe-connect/return").permitAll()
                    // OAuth2 / MCP discovery endpoints — must be public per RFC 8615
                    .requestMatchers("/.well-known/**").permitAll()
                    // Swagger UI — WebSecurityCustomizer was removed in Spring Security 7; permit explicitly
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs", "/api-docs/**").permitAll()
                    // Web Storefront browses a merchant's catalog anonymously — catalog reads only
                    .requestMatchers(HttpMethod.GET, *STOREFRONT_CATALOG_READS).permitAll()
                    // Web Storefront guest checkout — authorised in the controller by the order-scoped guest_token cookie
                    .requestMatchers(HttpMethod.POST, "/v1/storefront/orders", "/v1/storefront/orders/{orderId}/payment").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/storefront/orders/{orderId}", "/v1/storefront/orders/{orderId}/events").permitAll()
                    // Table Link resolution and Table availability — public by design, never expose orders
                    .requestMatchers(HttpMethod.GET, "/v1/storefront/tables/{tableCode}", "/v1/storefront/branches/{branchId:$ID}/table-availability").permitAll()
                    // Self-service signup from the apps — no account exists yet; the signup screen lists verticals first
                    .requestMatchers(HttpMethod.POST, "/v1/signup/shopper", "/v1/signup/merchant").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/merchants/verticals").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt(Customizer.withDefaults())
                // RFC 9728: point MCP clients straight at Keycloak for AS discovery
                it.protectedResourceMetadata { m ->
                    m.protectedResourceMetadataCustomizer { b ->
                        b.authorizationServer(issuerUri).tlsClientCertificateBoundAccessTokens(false)
                    }
                }
            }
            .build()

    private companion object {
        // UUID-only path segments so literal routes like /merchants/nearby stay protected.
        const val ID = "[0-9a-fA-F-]+"
        val STOREFRONT_CATALOG_READS = arrayOf(
            "/v1/merchants/{merchantId:$ID}",
            "/v1/merchants/{merchantId:$ID}/branches/{branchId:$ID}",
            "/v1/merchants/{merchantId:$ID}/products",
            "/v1/merchants/{merchantId:$ID}/products/{productId:$ID}",
            "/v1/merchants/{merchantId:$ID}/stripe-connect/status",
        )
    }
}
