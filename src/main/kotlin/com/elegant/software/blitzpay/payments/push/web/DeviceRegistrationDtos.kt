package com.elegant.software.blitzpay.payments.push.web

import com.elegant.software.blitzpay.payments.push.persistence.model.DevicePlatform
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class DeviceRegistrationRequest(
    @field:NotNull
    val orderId: String?,

    @field:NotNull
    @field:Pattern(regexp = "^ExponentPushToken\\[[^\\]]+\\]$")
    val expoPushToken: String?,

    val platform: DevicePlatform? = null,
)

data class DeviceRegistrationResponse(
    val id: UUID,
    val orderId: String,
    val expoPushToken: String,
    val platform: DevicePlatform?,
)
