package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.config.ApiException
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Base64
import java.util.UUID

internal data class CatalogScope(
    val merchantId: UUID,
    val branchId: UUID,
    val categoryId: UUID?,
    val uncategorized: Boolean,
    val includeInactive: Boolean,
) {
    fun cursor(createdAt: Instant, productId: UUID): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            (fields() + listOf(createdAt.toString(), productId.toString())).joinToString("|").toByteArray(Charsets.UTF_8)
        )

    fun position(cursor: String?): CatalogPosition? {
        if (cursor == null) return null
        try {
            require(cursor.length in 1..1024)
            val parts = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8).split('|')
            require(parts.size == 7 && parts.take(5) == fields())
            return CatalogPosition(Instant.parse(parts[5]), UUID.fromString(parts[6]))
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor for this catalog selection", "INVALID_CATALOG_CURSOR")
        } catch (_: java.time.format.DateTimeParseException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor for this catalog selection", "INVALID_CATALOG_CURSOR")
        }
    }

    private fun fields() = listOf(merchantId.toString(), branchId.toString(), categoryId?.toString().orEmpty(), uncategorized.toString(), includeInactive.toString())
}

internal data class CatalogPosition(val createdAt: Instant, val productId: UUID)
