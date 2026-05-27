package com.elegant.software.blitzpay.payments.braintree.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "psp-provider.braintree")
data class BraintreeProperties(
    val merchantId: String = "",
    val publicKey: String = "",
    val privateKey: String = "",
    val environment: String = "sandbox",
)
