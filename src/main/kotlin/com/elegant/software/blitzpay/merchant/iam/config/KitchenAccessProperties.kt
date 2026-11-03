package com.elegant.software.blitzpay.merchant.iam.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Settings for provisioning Kitchen Screen access (realm client + per-Branch kitchen user) in Keycloak. */
@ConfigurationProperties(prefix = "keycloak.iam.kitchen")
data class KitchenAccessProperties(
    /** Realm client the Kitchen Screen logs in with. */
    val clientId: String = "blitzpay-kitchen",
    /** Redirect and post-logout redirect URI of the Electron kiosk. */
    val redirectUri: String = "app://kitchen/renderer/index.html",
    /** Public API base URL the Kitchen Screen talks to; returned with the kitchen login. */
    val apiBaseUrl: String = "http://localhost:8080",
    /** Domain of the placeholder email set on kitchen users, so Keycloak's user profile never prompts the kiosk for one. */
    val emailDomain: String = "kitchen.invalid",
)
