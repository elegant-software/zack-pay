package com.elegant.software.blitzpay.config

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

/** The authenticated Kiosk: a restaurant-owned device bound to exactly one Branch of one Merchant. */
data class KioskPrincipal(val merchantId: UUID, val branchId: UUID)

/** Authentication of a Kiosk token. Carries only [KIOSK_AUTHORITY], never a Merchant, shopper or admin role. */
class KioskAuthentication(val principalData: KioskPrincipal) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority(KIOSK_AUTHORITY))) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): KioskPrincipal = principalData
    override fun getCredentials(): Any? = null

    companion object {
        const val KIOSK_ROLE = "KIOSK"
        const val KIOSK_AUTHORITY = "ROLE_$KIOSK_ROLE"
    }
}

/** Resolves an opaque Kiosk token (see [KIOSK_TOKEN_PREFIX]) to its Kiosk, or null when unknown, expired or revoked. */
fun interface KioskTokenVerifier {
    fun verify(token: String): KioskPrincipal?
}

/** Kiosk tokens are opaque and recognisable by this prefix; Keycloak JWTs never start with it. */
const val KIOSK_TOKEN_PREFIX = "kt_"
