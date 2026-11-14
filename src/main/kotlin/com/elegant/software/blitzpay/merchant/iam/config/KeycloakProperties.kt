package com.elegant.software.blitzpay.merchant.iam.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Two realms on one Keycloak server: [realm] (`blitzpay-business`) holds merchant users, platform admins,
 * Kitchen and Kiosk Logins; [customerRealm] (`blitzpay-customers`) holds shoppers, with email as username.
 * The admin service account [adminClientId] exists in both realms with the same secret.
 */
@ConfigurationProperties(prefix = "keycloak.iam")
data class KeycloakProperties(
    val serverUrl: String,
    val realm: String,
    val adminClientId: String,
    val adminClientSecret: String,
    val customerRealm: String = realm,
) {
    /** True when [issuer] is the customer realm's token issuer. */
    fun isCustomerIssuer(issuer: String?): Boolean = issuer?.trimEnd('/')?.endsWith("/realms/$customerRealm") == true
}
