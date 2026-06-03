package com.elegant.software.blitzpay.payments.truelayer.security

import com.elegant.software.blitzpay.payments.truelayer.config.TrueLayerProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Duration
import java.time.Instant

@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class TlSignatureVerifier(
    private val trueLayerProperties: TrueLayerProperties
) {
    // Simple cached JWK source so we don't fetch the JWKS on every verification.
    // Refresh interval is conservative; adjust if you need faster key rotation handling.
    private var cachedJwkSource: JWKSource<SecurityContext>? = null
    private var lastFetched: Instant? = null
    private val refreshInterval: Duration = Duration.ofMinutes(10)

    fun verify(tlSignatureJws: String, rawBody: String) {
        try {
            val jws = SignedJWT.parse(tlSignatureJws)

            val jwkSource = getJwkSource()

            val processor = DefaultJWTProcessor<SecurityContext>()
            processor.jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            processor.process(jws, null)

            val actualPayload = jws.payload.toString()
            if (actualPayload != rawBody) {
                error("TrueLayer webhook payload mismatch")
            }
        } catch (e: Exception) {
            throw IllegalStateException("TrueLayer signature verification failed", e)
        }
    }

    private fun getJwkSource(): JWKSource<SecurityContext> {
        val now = Instant.now()
        val last = lastFetched
        if (cachedJwkSource == null || last == null || Duration.between(last, now) > refreshInterval) {
            val jwksJson = URL(trueLayerProperties.webhookJku).openStream().bufferedReader().use { it.readText() }
            val jwkSet = JWKSet.parse(jwksJson)
            cachedJwkSource = ImmutableJWKSet(jwkSet)
            lastFetched = now
        }
        return cachedJwkSource!!
    }
}
