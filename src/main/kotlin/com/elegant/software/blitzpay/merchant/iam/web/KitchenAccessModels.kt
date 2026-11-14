package com.elegant.software.blitzpay.merchant.iam.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * The Kitchen Login of a Branch, returned once by `POST .../kitchen/enable`. The password is generated per call and
 * exists only in this response: it is never stored, and [toString] masks it so it can't end up in a log line.
 */
data class KitchenLoginResponse(
    val username: String,
    val password: String,
    val keycloakUrl: String,
    val realm: String,
    val clientId: String,
    val apiBaseUrl: String,
    val branchId: UUID,
) {
    override fun toString(): String =
        "KitchenLoginResponse(username=$username, password=****, keycloakUrl=$keycloakUrl, realm=$realm, " +
            "clientId=$clientId, apiBaseUrl=$apiBaseUrl, branchId=$branchId)"
}

/** Whether the Branch's Kitchen Login is enabled. `username` is null when no Kitchen Login was ever provisioned. */
data class KitchenAccessStatusResponse(
    val enabled: Boolean,
    val username: String?,
)


/**
 * Optional body of the Kitchen and Kiosk login enable/reset calls: the password the admin chose (8 to 128 characters).
 * Without it a strong password is generated. Either way it is returned once and never stored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DeviceLoginPasswordRequest(val password: String? = null) {
    override fun toString(): String = "DeviceLoginPasswordRequest(password=${if (password == null) null else "****"})"
}
