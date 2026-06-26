package com.elegant.software.blitzpay.payments.truelayer.security

import com.elegant.software.blitzpay.payments.truelayer.config.TlWebhookProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true")
@EnableConfigurationProperties(TlWebhookProperties::class)
@Service
class JwksService(private val props: TlWebhookProperties) {
    private val client = WebClient.builder().build()
    @Volatile private var cached: String? = null
    @Volatile private var cachedAt: Long = 0

    fun fetchJwks(): String {
        val now = System.currentTimeMillis()
        val refreshMs = Duration.ofMinutes(10).toMillis()
        val current = cached
        if (current != null && (now - cachedAt) < refreshMs) return current

        val jwks = client.get()
            .uri(props.allowedJku)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: error("Empty JWKS response from ${props.allowedJku}")

        cached = jwks
        cachedAt = System.currentTimeMillis()
        return jwks
    }
}
