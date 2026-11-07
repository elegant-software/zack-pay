package com.elegant.software.blitzpay.merchant.api

/** Semantic color roles, shared by all clients. Foreground/background pairs are validated together. */
data class ThemePalette(
    val primary: String = "#00C2FF", val onPrimary: String = "#000000",
    val accent: String = "#5856D6", val onAccent: String = "#FFFFFF",
    val background: String = "#FFFFFF", val onBackground: String = "#1C1C1E",
    val surface: String = "#F2F2F7", val onSurface: String = "#1C1C1E",
    val card: String = "#FFFFFF", val onCard: String = "#1C1C1E",
    val muted: String = "#636366", val border: String = "#767676",
    val error: String = "#B3261E", val onError: String = "#FFFFFF",
    val success: String = "#146C2E", val onSuccess: String = "#FFFFFF",
    val warning: String = "#805600", val onWarning: String = "#FFFFFF",
) {
    companion object {
        fun dark() = ThemePalette(primary = "#70D7FF", accent = "#C4BFFF", onAccent = "#000000",
            background = "#111113", onBackground = "#F2F2F7", surface = "#1C1C1E", onSurface = "#F2F2F7",
            card = "#242426", onCard = "#F2F2F7", muted = "#AEAEB2", border = "#8E8E93",
            error = "#FFB4AB", onError = "#000000", success = "#83DB98", onSuccess = "#000000",
            warning = "#FFD180", onWarning = "#000000")
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Complete replacement. Null fields inherit; empty hiddenFeatures removes local exclusions. */
data class WhiteLabelSettings(
    val enabled: Boolean? = null,
    val displayName: String? = null,
    val title: String? = null,
    val logoStorageKey: String? = null,
    val darkLogoStorageKey: String? = null,
    val themeMode: ThemeMode? = null,
    val light: ThemePalette? = null,
    val dark: ThemePalette? = null,
    val hiddenFeatures: Set<String> = emptySet(),
)

data class WhiteLabelUpdate(val settings: WhiteLabelSettings, val revision: Long = -1)

data class EffectiveWhiteLabel(
    val enabled: Boolean, val displayName: String, val title: String,
    val logoUrl: String?, val darkLogoUrl: String?, val themeMode: ThemeMode,
    val light: ThemePalette, val dark: ThemePalette, val features: Set<String>,
)

data class WhiteLabelResponse(
    val settings: WhiteLabelSettings, val revision: Long, val effective: EffectiveWhiteLabel,
    val availableFeatures: Set<String>, val inherited: Boolean,
    val logoPreviewUrl: String? = null, val darkLogoPreviewUrl: String? = null,
    val inheritedConfiguration: EffectiveWhiteLabel? = null,
)
