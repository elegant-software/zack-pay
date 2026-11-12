package com.elegant.software.blitzpay.merchant.iam.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/** What a Merchant Manager configures for one Branch's Kiosk. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KioskSettings(
    /** Enabled Kiosk Payment Choices: `CARD`, `CASH`, `SCAN_TO_PAY`; at least one. */
    val paymentChoices: List<String> = listOf("CARD", "CASH", "SCAN_TO_PAY"),
    /** Seconds the confirmation screen shows before the Kiosk resets (5..300). */
    val resetSeconds: Int = 30,
    /** Offered languages, a subset of the Merchant's Supported Menu Languages; empty = original language only. */
    val languages: List<String> = emptyList(),
    /** Advertised Products: active Products of the Branch; empty = the Kiosk falls back to Products with images. */
    val advertisedProductIds: List<String> = emptyList(),
)

/** The Kiosk Login of a Branch, returned once by `POST .../kiosk/enable`; [toString] masks the password. */
data class KioskLoginResponse(val login: String, val password: String) {
    override fun toString(): String = "KioskLoginResponse(login=$login, password=****)"
}

/** Kiosk status of a Branch; `login` is null when no Kiosk Login was ever provisioned. Never includes the password. */
data class KioskStatusResponse(val enabled: Boolean, val login: String?, val settings: KioskSettings)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KioskLoginRequest(val login: String, val password: String)

data class KioskSessionResponse(val token: String, val merchantId: UUID, val branchId: UUID)
