package com.elegant.software.blitzpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "api")
data class ApiVersionProperties(
    val defaultVersion: String = "1",
    val versions: Versions = Versions()
) {
    data class Versions(
        val invoice: String = "1",
        val qrpay: String = "1",
        val truelayer: String = "1",
        val payments: String = "1",
        val mobileObservability: String = "1",
        val merchant: String = "1",
        val order: String = "1",
        val appointment: String = "1",
        val voice: String = "1",
    )
}
