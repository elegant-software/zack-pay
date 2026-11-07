package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.ThemePalette
import kotlin.math.pow

object ThemePaletteValidator {
    fun validate(p: ThemePalette, path: String) {
        val colors = mapOf("primary" to p.primary, "onPrimary" to p.onPrimary, "accent" to p.accent, "onAccent" to p.onAccent,
            "background" to p.background, "onBackground" to p.onBackground, "surface" to p.surface, "onSurface" to p.onSurface,
            "card" to p.card, "onCard" to p.onCard, "muted" to p.muted, "border" to p.border, "error" to p.error,
            "onError" to p.onError, "success" to p.success, "onSuccess" to p.onSuccess, "warning" to p.warning, "onWarning" to p.onWarning)
        colors.forEach { (name, color) -> require(Regex("^#[0-9a-fA-F]{6}$").matches(color)) { "$path.$name must be a six-digit hex color" } }
        listOf("primary" to "onPrimary", "accent" to "onAccent", "background" to "onBackground",
            "surface" to "onSurface", "card" to "onCard", "error" to "onError", "success" to "onSuccess", "warning" to "onWarning",
            "background" to "muted", "surface" to "muted", "card" to "muted").forEach { (bg, fg) ->
            require(contrast(colors.getValue(bg), colors.getValue(fg)) >= 4.5) { "$path.$fg must have at least 4.5:1 contrast against $bg" }
        }
        listOf("background", "surface", "card").forEach { bg ->
            require(contrast(p.border, colors.getValue(bg)) >= 3) { "$path.border must have at least 3:1 contrast against $bg" }
        }
    }

    private fun luminance(color: String): Double {
        val c = listOf(1, 3, 5).map {
            val v = color.substring(it, it + 2).toInt(16) / 255.0
            if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return c[0] * 0.2126 + c[1] * 0.7152 + c[2] * 0.0722
    }

    private fun contrast(a: String, b: String): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
