package com.elegant.software.blitzpay.merchant.iam.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/** What a Merchant Manager configures for one Branch's Kiosks. */
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateKioskRequest(
    /** Shown in the web admin, e.g. "Entrance"; defaults to "Kiosk <number>". Also names the login: `<branch>-<kiosk>`. */
    val name: String? = null,
    /** Password the admin chose (8 to 128 characters); generated when absent. Returned once, never stored. */
    val password: String? = null,
) {
    override fun toString(): String = "CreateKioskRequest(name=$name, password=${if (password == null) null else "****"})"
}

/** A Kiosk of a Branch. Never includes the password. */
data class KioskResponse(
    val id: UUID,
    val number: Int,
    val name: String,
    val username: String,
    val enabled: Boolean,
)

/**
 * The Kiosk Login of one Kiosk, returned once when the Kiosk is added or its login enabled or reset. Same shape as the
 * Kitchen Login plus the Kiosk. The password exists only in this response, and [toString] masks it.
 */
data class KioskLoginResponse(
    val kioskId: UUID,
    val name: String,
    val username: String,
    val password: String,
    val keycloakUrl: String,
    val realm: String,
    val clientId: String,
    val apiBaseUrl: String,
    val branchId: UUID,
) {
    override fun toString(): String =
        "KioskLoginResponse(kioskId=$kioskId, name=$name, username=$username, password=****, keycloakUrl=$keycloakUrl, " +
            "realm=$realm, clientId=$clientId, apiBaseUrl=$apiBaseUrl, branchId=$branchId)"
}
