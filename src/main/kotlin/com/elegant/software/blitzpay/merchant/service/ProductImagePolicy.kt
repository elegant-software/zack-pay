package com.elegant.software.blitzpay.merchant.service

object ProductImagePolicy {
    const val MaxBytes: Long = 5L * 1024L * 1024L

    /** Larger sources (URL or base64) are accepted and shrunk server-side to fit [MaxBytes]. */
    const val MaxSourceBytes: Long = 15L * 1024L * 1024L

    /** Decompression-bomb guard, checked from the image header before any pixels are decoded. */
    const val MaxSourcePixels: Long = 100_000_000L
    const val MaxDescriptionLength: Int = 2_000

    private val extensionsByContentType = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp"
    )

    fun extensionFor(contentType: String): String =
        extensionsByContentType[contentType.lowercase()]
            ?: throw IllegalArgumentException("Unsupported product image content type: $contentType")

    fun validateSize(size: Long) {
        require(size <= MaxBytes) { "Product image must be 5 MB or smaller" }
    }

    fun validateSourceSize(size: Long) {
        require(size <= MaxSourceBytes) { "Product image source must be 15 MB or smaller" }
    }

    fun normalizeDescription(description: String?): String? =
        description?.trim()?.ifBlank { null }?.also {
            require(it.length <= MaxDescriptionLength) {
                "Product description must be ${MaxDescriptionLength} characters or fewer"
            }
        }
}
