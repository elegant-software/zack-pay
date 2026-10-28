package com.elegant.software.blitzpay.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

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
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .build()
}
