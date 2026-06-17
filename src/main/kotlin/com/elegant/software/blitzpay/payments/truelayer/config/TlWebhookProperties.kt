
package com.elegant.software.blitzpay.payments.truelayer.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "truelayer.webhooks")
data class TlWebhookProperties(
    /** "sandbox" or "live" */
    val environment: String,
    /** Max accepted age of the webhook timestamp header, e.g. PT5M */
    val maxSkew: String
) {
    val allowedJku: String
        get() = when (environment.lowercase()) {
            "live"    -> "https://webhooks.truelayer.com/.well-known/jwks"
            else      -> "https://webhooks.truelayer-sandbox.com/.well-known/jwks"
        }
}
