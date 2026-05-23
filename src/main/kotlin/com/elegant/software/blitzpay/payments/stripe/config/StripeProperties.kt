package com.elegant.software.blitzpay.payments.stripe.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "stripe")
data class StripeProperties(
    val secretKey: String = "",
    val publishableKey: String = "",
    val webhookSecret: String = "",
    val connectWebhookSecret: String = "",
    val applicationFeeAmount: Long = 0,
    val connect: ConnectUrls = ConnectUrls(),
) {
    data class ConnectUrls(
        val returnUrl: String = "",
        val apiBaseUrl: String = "",
        val appDeepLink: String = "",
    )
}
