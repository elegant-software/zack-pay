package com.elegant.software.blitzpay.merchant.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

enum class ThemeApp { SHOPPER, MERCHANT }

data class MerchantThemeRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$") val primaryColor: String,
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$") val accentColor: String,
)

data class MerchantThemeResponse(
    val id: UUID,
    val merchantId: UUID,
    val name: String,
    val primaryColor: String,
    val primaryTextColor: String,
    val accentColor: String,
    val accentTextColor: String,
    val app: ThemeApp = ThemeApp.SHOPPER,
)
