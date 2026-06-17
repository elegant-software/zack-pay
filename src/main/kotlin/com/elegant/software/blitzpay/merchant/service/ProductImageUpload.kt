package com.elegant.software.blitzpay.merchant.service

data class ProductImageUpload(
    val contentType: String,
    val bytes: ByteArray
) {
    init {
        ProductImagePolicy.extensionFor(contentType)
        ProductImagePolicy.validateSize(bytes.size.toLong())
    }

    val extension: String = ProductImagePolicy.extensionFor(contentType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductImageUpload) return false
        return contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * contentType.hashCode() + bytes.contentHashCode()
}
