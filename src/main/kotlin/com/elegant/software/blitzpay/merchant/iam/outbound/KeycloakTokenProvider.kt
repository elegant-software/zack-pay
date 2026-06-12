package com.elegant.software.blitzpay.merchant.iam.outbound

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import com.elegant.software.blitzpay.config.propagateRequestId
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
@Profile("!contract-test")
class KeycloakTokenProvider(
    private val properties: KeycloakProperties,
    restClientBuilder: RestClient.Builder,
) {

    private val log = LoggerFactory.getLogger(KeycloakTokenProvider::class.java)
    private val cached = AtomicReference<CachedToken?>()
    private val tokenClient = restClientBuilder.propagateRequestId().baseUrl(properties.serverUrl).build()

    fun getToken(): String {
        val current = cached.get()
        if (current != null && current.expiresAt.isAfter(Instant.now())) return current.token
        return fetchToken()
    }

    fun invalidate() {
        cached.set(null)
        log.debug("keycloak token cache invalidated")
    }

    private fun fetchToken(): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", properties.adminClientId)
            add("client_secret", properties.adminClientSecret)
        }
        val response = tokenClient.post()
            .uri("/realms/${properties.realm}/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(KeycloakTokenResponse::class.java)
            ?: throw IllegalStateException("Empty response from Keycloak token endpoint")

        val expiresAt = Instant.now().plusSeconds(response.expires_in - 30)
        cached.set(CachedToken(response.access_token, expiresAt))
        log.debug("keycloak token fetched expiresIn={}s", response.expires_in)
        return response.access_token
    }

    private data class CachedToken(val token: String, val expiresAt: Instant)
}
