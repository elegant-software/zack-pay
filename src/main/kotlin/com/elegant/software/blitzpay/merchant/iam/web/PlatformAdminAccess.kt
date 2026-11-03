package com.elegant.software.blitzpay.merchant.iam.web

import org.springframework.security.oauth2.jwt.Jwt

/**
 * Platform admin check from the verified Keycloak access token: the realm role `blitzpay-admin` in
 * `realm_access.roles`, the role the web admin dashboard requires to sign in. Client roles do not count.
 */
object PlatformAdminAccess {
    const val PLATFORM_ADMIN_ROLE = "blitzpay-admin"

    fun isPlatformAdmin(jwt: Jwt?): Boolean {
        val realmAccess = jwt?.getClaim<Any>("realm_access") as? Map<*, *> ?: return false
        val roles = realmAccess["roles"] as? Collection<*> ?: return false
        return PLATFORM_ADMIN_ROLE in roles
    }
}
