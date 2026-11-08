package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * A named storefront look a merchant can pick. Colours are data ([tokens]); the [style] is a layout
 * the storefront hard-codes in CSS (fonts, radii), so it is the only part that needs a release.
 */
@Entity
@Table(name = "storefront_themes", schema = "blitzpay")
class StorefrontTheme(
    @Id
    @Column(nullable = false, length = 32)
    val code: String,

    @Column(name = "display_name", nullable = false, length = 64)
    var displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var style: StorefrontThemeStyle = StorefrontThemeStyle.STANDARD,

    /** `{"light": {"primary": "#926b1d", …}, "dark": {…}}`; keys from [StorefrontThemeTokens.KEYS]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var tokens: Map<String, Map<String, String>> = emptyMap(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: StorefrontThemeStatus = StorefrontThemeStatus.DRAFT,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    companion object {
        const val DEFAULT = "DEFAULT"
    }
}

/** Layout variants built into the storefront CSS; adding one needs a storefront release. */
enum class StorefrontThemeStyle {
    STANDARD,
    /** Serif headings, square corners, roomier cards. */
    CLASSIC,
}

/** Merchants can only pick PUBLISHED themes; DRAFT themes can be opened with the storefront `?previewTheme=` link. */
enum class StorefrontThemeStatus { DRAFT, PUBLISHED }

/** The CSS colour variables (`--storefront-<key>`) a theme may set, per light/dark mode. */
object StorefrontThemeTokens {
    val MODES = setOf("light", "dark")
    val KEYS = setOf(
        "ink", "muted", "primary", "mint", "stroke", "bg", "warm", "surface",
        "action", "action-hover", "positive", "glow", "danger-bg", "danger-stroke", "danger",
    )
}
