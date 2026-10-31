package com.elegant.software.blitzpay.merchant.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Technical quality gate for product images submitted through MCP. Defaults are deliberately
 * lenient; tune them from the scores logged by ProductImageQualityInspector.
 */
@ConfigurationProperties(prefix = "blitzpay.product-image.quality")
data class ProductImageQualityProperties(
    val enabled: Boolean = true,
    /** Minimum length of the shorter side, in source pixels (after crop). */
    val minShortEdge: Int = 400,
    /** Maximum ratio of the longer side to the shorter side. */
    val maxAspectRatio: Double = 4.0,
    /** Minimum sharpness: 90th-percentile tile variance of the Laplacian at 1024 px. */
    val minSharpness: Double = 40.0,
    /** Allowed mean luminance range (0–255); high upper bound so white-background shots pass. */
    val minBrightness: Double = 20.0,
    val maxBrightness: Double = 250.0,
    /** Minimum luminance standard deviation; rejects blank or near-solid images. */
    val minContrast: Double = 10.0
)
