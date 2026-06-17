package com.elegant.software.blitzpay.merchant.service

import java.util.UUID

object MerchantLogoPolicy {
    private val extensionsByContentType = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp"
    )

    fun extensionFor(contentType: String): String =
        extensionsByContentType[contentType.lowercase()]
            ?: throw IllegalArgumentException("Unsupported merchant logo content type: $contentType")

    fun storageKeyFor(merchantId: UUID, contentType: String): String =
        "merchants/$merchantId/logo.${extensionFor(contentType)}"

    fun validateStorageKey(merchantId: UUID, storageKey: String) {
        val suffix = storageKey.substringAfterLast('.', "")
        require(suffix in setOf("jpg", "jpeg", "png", "webp")) {
            "Merchant logo storageKey must end with .jpg, .png, or .webp"
        }

        val normalizedKey = if (suffix == "jpeg") {
            storageKey.removeSuffix(".jpeg") + ".jpg"
        } else {
            storageKey
        }

        require(normalizedKey == "merchants/$merchantId/logo.${normalizedKey.substringAfterLast('.')}") {
            "Merchant logo storageKey must match merchants/$merchantId/logo.{jpg|png|webp}"
        }
    }
}
