package com.elegant.software.blitzpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("*"),
    /** Extra origins for the Kitchen Screen endpoints only: the Electron renderer is served from `app://kitchen` (ADR-0022). */
    val kitchenAllowedOrigins: List<String> = listOf("app://kitchen"),
)
