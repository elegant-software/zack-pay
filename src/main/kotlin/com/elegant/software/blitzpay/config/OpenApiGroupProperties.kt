package com.elegant.software.blitzpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blitzpay.openapi")
data class OpenApiGroupProperties(
    val primaryGroupLabel: String = "",
    val groups: Groups = Groups(),
) {
    data class Groups(
        val invoice: Group = Group(),
        val qrpay: Group = Group(),
        val truelayer: Group = Group(),
        val support: Group = Group(),
        val general: Group = Group(),
        val pushNotifications: Group = Group(),
        val stripe: Group = Group(),
        val braintree: Group = Group(),
        val mobileGeofencing: Group = Group(),
        val merchant: Group = Group(),
        val order: Group = Group(),
        val appointment: Group = Group(),
        val voice: Group = Group(),
        val actuator: Group = Group(),
    )

    data class Group(
        val label: String = "",
    )
}
