package com.elegant.software.blitzpay.merchant.api

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** PUT replaces the complete settings; null fields inherit from the parent/defaults. */
data class BrandingSettings(
    val enabled: Boolean? = null,
    @field:Size(min = 1, max = 120) val displayName: String? = null,
    @field:Size(max = 512) val logoStorageKey: String? = null,
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$") val primaryColor: String? = null,
    @field:Pattern(regexp = "^#[0-9a-fA-F]{6}$") val accentColor: String? = null,
)

data class EffectiveBranding(
    val platformName: String = "Simpleio",
    val enabled: Boolean,
    val displayName: String,
    val logoUrl: String?,
    val primaryColor: String,
    val primaryTextColor: String,
    val accentColor: String,
    val accentTextColor: String,
)

data class BrandingResponse(
    val settings: BrandingSettings,
    val effective: EffectiveBranding,
    val logoPreviewUrl: String? = null,
)
