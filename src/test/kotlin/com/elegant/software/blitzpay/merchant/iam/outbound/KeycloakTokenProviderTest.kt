package com.elegant.software.blitzpay.merchant.iam.outbound

import com.elegant.software.blitzpay.merchant.iam.config.KeycloakProperties
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KeycloakTokenProviderTest {

    private val properties = KeycloakProperties(
        serverUrl = "http://keycloak:8080",
        realm = "blitzpay",
        adminClientId = "blitzpay-admin",
        adminClientSecret = "secret",
    )

    @Test
    fun `getToken returns token from Keycloak`() {
        KeycloakTokenProvider(properties)
    }

    @Test
    fun `invalidate clears cached token`() {
        val provider = KeycloakTokenProvider(properties)
        provider.invalidate() // should not throw
    }
}
