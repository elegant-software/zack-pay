package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeStatus
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeStyle

data class StorefrontThemeResponse(
    val code: String,
    val displayName: String,
    val style: StorefrontThemeStyle,
    val tokens: Map<String, Map<String, String>>,
    val status: StorefrontThemeStatus,
    /** Readability warnings from the last save, e.g. low text contrast; empty on reads. */
    val warnings: List<String> = emptyList(),
)

data class SaveStorefrontThemeRequest(
    val displayName: String,
    val style: StorefrontThemeStyle = StorefrontThemeStyle.STANDARD,
    val tokens: Map<String, Map<String, String>> = emptyMap(),
    val status: StorefrontThemeStatus = StorefrontThemeStatus.DRAFT,
)
