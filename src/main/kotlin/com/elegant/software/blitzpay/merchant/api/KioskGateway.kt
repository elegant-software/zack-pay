package com.elegant.software.blitzpay.merchant.api

import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/** What other modules need to know about Kiosks. */
interface KioskGateway {
    /** The enabled Kiosk Payment Choices of the Branch (`CARD`, `CASH`, `SCAN_TO_PAY`); all three when never configured. */
    fun findKioskPaymentChoices(branchId: UUID): Set<String>

    /** True when [jwt] was issued to the Kiosk realm client, i.e. the caller is a Kiosk signed in with its Kiosk Login. */
    fun isKioskToken(jwt: Jwt?): Boolean
}
