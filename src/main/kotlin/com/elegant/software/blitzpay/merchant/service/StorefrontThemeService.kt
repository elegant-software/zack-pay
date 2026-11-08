package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.SaveStorefrontThemeRequest
import com.elegant.software.blitzpay.merchant.api.StorefrontThemeResponse
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontTheme
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeStatus
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeTokens
import com.elegant.software.blitzpay.merchant.persistence.repository.StorefrontThemeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.math.pow

@Service
@Transactional(readOnly = true)
class StorefrontThemeService(private val repository: StorefrontThemeRepository) {

    fun list(includeDrafts: Boolean = false): List<StorefrontThemeResponse> =
        (if (includeDrafts) repository.findAll().sortedBy { it.displayName }
        else repository.findAllByStatusOrderByDisplayName(StorefrontThemeStatus.PUBLISHED)).map { it.toResponse() }

    /** Any status, so a DRAFT can be previewed on the storefront before merchants can pick it. */
    fun get(code: String): StorefrontThemeResponse =
        find(code).toResponse()

    /** A merchant may only switch to a PUBLISHED theme. */
    fun requireSelectable(code: String) {
        require(find(code).status == StorefrontThemeStatus.PUBLISHED) { "Storefront theme '$code' is not published" }
    }

    /**
     * Creates or replaces a DRAFT. The values end up in the storefront's CSS, so only known colour
     * variables with `#rrggbb` values are accepted; poor contrast is only a warning, the admin
     * preview is where a person judges the look.
     */
    @Transactional
    fun save(code: String, request: SaveStorefrontThemeRequest): StorefrontThemeResponse {
        require(CODE.matches(code)) { "code must be 2-32 upper-case letters, digits or underscores, starting with a letter" }
        require(code != StorefrontTheme.DEFAULT) { "The DEFAULT theme can't be changed" }
        val displayName = request.displayName.trim()
        require(displayName.isNotEmpty() && displayName.length <= 64) { "displayName must be 1-64 characters" }
        val tokens = normalize(request.tokens)

        val now = Instant.now()
        val theme = repository.findById(code).orElse(null)?.also {
            require(it.status == StorefrontThemeStatus.DRAFT) {
                "Published theme '$code' can't be changed; save the new look as a new draft and move merchants to it"
            }
            it.displayName = displayName
            it.style = request.style
            it.tokens = tokens
            it.status = request.status
            it.updatedAt = now
        } ?: StorefrontTheme(code, displayName, request.style, tokens, request.status, now)
        return repository.save(theme).toResponse(contrastWarnings(tokens))
    }

    private fun find(code: String): StorefrontTheme =
        repository.findById(code).orElseThrow { NoSuchElementException("Storefront theme not found: $code") }

    private fun normalize(tokens: Map<String, Map<String, String>>): Map<String, Map<String, String>> {
        val unknownModes = tokens.keys - StorefrontThemeTokens.MODES
        require(unknownModes.isEmpty()) { "Unknown theme modes $unknownModes; use ${StorefrontThemeTokens.MODES}" }
        return tokens.mapValues { (mode, colours) ->
            val unknownKeys = colours.keys - StorefrontThemeTokens.KEYS
            require(unknownKeys.isEmpty()) { "Unknown $mode colours $unknownKeys; use ${StorefrontThemeTokens.KEYS}" }
            colours.mapValues { (key, value) ->
                require(HEX.matches(value)) { "$mode.$key must be a #rrggbb colour" }
                value.lowercase()
            }
        }
    }

    private fun contrastWarnings(tokens: Map<String, Map<String, String>>): List<String> =
        tokens.flatMap { (mode, c) ->
            CONTRAST_PAIRS.mapNotNull { (fg, bg) ->
                val fgHex = if (fg == WHITE) WHITE else c[fg] ?: return@mapNotNull null
                val bgHex = c[bg] ?: return@mapNotNull null
                val ratio = contrast(fgHex, bgHex)
                if (ratio >= 4.5) null
                else "$mode: ${if (fg == WHITE) "white button text" else fg} on $bg has contrast %.1f:1, below 4.5:1".format(ratio)
            }
        }

    private fun StorefrontTheme.toResponse(warnings: List<String> = emptyList()) =
        StorefrontThemeResponse(code, displayName, style, tokens, status, warnings)

    companion object {
        private val CODE = Regex("^[A-Z][A-Z0-9_]{1,31}$")
        private val HEX = Regex("^#[0-9a-fA-F]{6}$")
        private const val WHITE = "#ffffff"
        /** Text/background pairs the storefront actually renders; buttons use white text on `action`. */
        private val CONTRAST_PAIRS = listOf("ink" to "bg", "ink" to "surface", "muted" to "surface", WHITE to "action")

        /** WCAG 2 contrast ratio between two `#rrggbb` colours. */
        fun contrast(a: String, b: String): Double {
            val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
            return (hi + 0.05) / (lo + 0.05)
        }

        private fun luminance(hex: String): Double {
            val (r, g, b) = (0..2).map { i ->
                val c = hex.substring(1 + i * 2, 3 + i * 2).toInt(16) / 255.0
                if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
    }
}
