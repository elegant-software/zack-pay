package com.elegant.software.blitzpay.payments.adyen.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "psp-provider.adyen")
data class AdyenProperties(
    val apiKey: String = "",
    val environment: String = "TEST",          // TEST or LIVE
    val liveUrlPrefix: String = "",            // required for LIVE environment
    val hmacKey: String = "",                  // webhook HMAC key
    val platformBalanceAccountId: String = "", // BlitzPay's own balance account
    val applicationFeeAmount: Long = 0,        // platform fee in minor units
)
