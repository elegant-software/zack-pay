package com.elegant.software.blitzpay.merchant.api

/** Public interface metadata only; no merchant configuration, capabilities or credentials. */
data class StorefrontLanguageResponse(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flagCode: String,
    val direction: String,
)
