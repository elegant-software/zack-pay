package com.elegant.software.blitzpay.merchant.iam.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** What a shared Branch device login (Kitchen Login, Kiosk Login) needs: its Keycloak realm client and where it talks to. */
interface BranchLoginProperties {
    /** Realm client the device logs in with. */
    val clientId: String
    /** Redirect and post-logout redirect URI of the device app. */
    val redirectUri: String
    /** Public API base URL the device talks to; returned with the login. */
    val apiBaseUrl: String
    /** Domain of the placeholder email set on the device users, so Keycloak's user profile never prompts for one. */
    val emailDomain: String
}

/** Settings for provisioning Kitchen Screen access (realm client + per-Branch kitchen user) in Keycloak. */
@ConfigurationProperties(prefix = "keycloak.iam.kitchen")
data class KitchenAccessProperties(
    override val clientId: String = "blitzpay-kitchen",
    override val redirectUri: String = "app://kitchen/renderer/index.html",
    override val apiBaseUrl: String = "http://localhost:8080",
    override val emailDomain: String = "kitchen.invalid",
) : BranchLoginProperties

/** Settings for provisioning Kiosk access (realm client + one user per Kiosk) in Keycloak. */
@ConfigurationProperties(prefix = "keycloak.iam.kiosk")
data class KioskAccessProperties(
    override val clientId: String = KIOSK_CLIENT_ID,
    override val redirectUri: String = "app://kiosk/renderer/index.html",
    override val apiBaseUrl: String = "http://localhost:8080",
    override val emailDomain: String = "kiosk.invalid",
) : BranchLoginProperties {
    companion object {
        const val KIOSK_CLIENT_ID = "blitzpay-kiosk"
    }
}
