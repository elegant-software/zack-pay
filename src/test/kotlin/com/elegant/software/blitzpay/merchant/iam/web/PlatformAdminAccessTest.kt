package com.elegant.software.blitzpay.merchant.iam.web

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformAdminAccessTest {

    private fun jwt(claims: Map<String, Any>) = Jwt.withTokenValue("t").header("alg", "none").subject("u")
        .claims { it.putAll(claims) }.build()

    @Test
    fun `a token with the blitzpay-admin realm role is a platform admin`() {
        assertTrue(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("realm_access" to mapOf("roles" to listOf("offline_access", "blitzpay-admin"))))))
    }

    @Test
    fun `other realm roles, client roles, a top-level roles claim and missing claims are not`() {
        assertFalse(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("realm_access" to mapOf("roles" to listOf("offline_access"))))))
        assertFalse(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("resource_access" to mapOf("blitzpay-admin-dashboard" to mapOf("roles" to listOf("blitzpay-admin")))))))
        assertFalse(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("roles" to listOf("blitzpay-admin")))))
        assertFalse(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("realm_access" to "blitzpay-admin"))))
        assertFalse(PlatformAdminAccess.isPlatformAdmin(jwt(mapOf("groups" to listOf("blitzpay-admin")))))
        assertFalse(PlatformAdminAccess.isPlatformAdmin(null))
    }
}
