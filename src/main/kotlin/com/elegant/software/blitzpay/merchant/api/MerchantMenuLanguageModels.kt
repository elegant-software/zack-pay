package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.TranslationOrigin
import java.time.Instant
import java.util.UUID

data class MenuLanguageResponse(
    val code: String,
    val displayName: String,
)

enum class CatalogItemType {
    PRODUCT,
    CATEGORY,
}

data class CatalogTranslationResponse(
    val language: String,
    val name: String,
    val description: String? = null,
    val origin: TranslationOrigin,
    val stale: Boolean,
    val updatedAt: Instant,
)

data class UpsertCatalogTranslationRequest(
    val name: String,
    val description: String? = null,
)

data class PendingTranslationItem(
    val type: CatalogItemType,
    val id: UUID,
    val originalName: String,
    val originalDescription: String? = null,
    val translatedName: String? = null,
    val translatedDescription: String? = null,
    val origin: TranslationOrigin? = null,
    val stale: Boolean,
)

data class PendingTranslationsResponse(
    val merchantId: UUID,
    val originalMenuLanguage: String,
    val language: String,
    val totalPending: Int,
    val items: List<PendingTranslationItem>,
)

data class CatalogTranslationInput(
    val type: CatalogItemType,
    val id: String,
    val name: String,
    val description: String? = null,
)

data class TranslatedCatalogItem(
    val type: CatalogItemType,
    val id: UUID,
    val name: String,
)

data class CatalogTranslationUpsertResult(
    val created: List<TranslatedCatalogItem>,
    val updated: List<TranslatedCatalogItem>,
    val skipped: List<BulkSkippedItem>,
    val failed: List<BulkFailedItem>,
)

data class TranslationCoverageResponse(
    val language: String,
    val productsTotal: Int,
    val productsTranslated: Int,
    val categoriesTotal: Int,
    val categoriesTranslated: Int,
)
