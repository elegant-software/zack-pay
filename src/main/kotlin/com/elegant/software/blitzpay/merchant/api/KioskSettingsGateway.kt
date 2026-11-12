package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

/** Read access to the Kiosk Settings of a Branch for other modules. */
interface KioskSettingsGateway {
    /** The enabled Kiosk Payment Choices of the Branch (`CARD`, `CASH`, `SCAN_TO_PAY`); all three when never configured. */
    fun findKioskPaymentChoices(branchId: UUID): Set<String>
}
