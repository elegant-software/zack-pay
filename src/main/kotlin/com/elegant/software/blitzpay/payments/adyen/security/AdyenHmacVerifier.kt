package com.elegant.software.blitzpay.payments.adyen.security

import com.adyen.util.HMACValidator
import com.elegant.software.blitzpay.payments.adyen.config.AdyenProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.SignatureException

/**
 * Verifies Adyen webhook HMAC-SHA256 signatures for Balance Platform configuration webhooks.
 * Adyen sends the HMAC signature in the `HmacSignature` header.
 */
@Component
class AdyenHmacVerifier(private val adyenProperties: AdyenProperties) {

    private val log = LoggerFactory.getLogger(AdyenHmacVerifier::class.java)
    private val hmacValidator = HMACValidator()

    /**
     * Verifies that the provided HMAC signature matches the computed HMAC of the payload
     * using the configured HMAC key.
     *
     * @throws AdyenWebhookSignatureException if the signature is invalid or verification fails
     */
    fun verify(signature: String, payload: String) {
        if (adyenProperties.hmacKey.isBlank()) {
            log.warn("adyen webhook hmac key not configured — skipping signature verification")
            return
        }
        try {
            val valid = hmacValidator.validateHMAC(signature, adyenProperties.hmacKey, payload)
            if (!valid) {
                log.warn("adyen webhook signature invalid")
                throw AdyenWebhookSignatureException("Adyen webhook HMAC signature verification failed")
            }
        } catch (ex: SignatureException) {
            log.warn("adyen webhook signature verification error={}", ex.message)
            throw AdyenWebhookSignatureException("Adyen webhook HMAC verification error: ${ex.message}", ex)
        }
    }
}

class AdyenWebhookSignatureException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
