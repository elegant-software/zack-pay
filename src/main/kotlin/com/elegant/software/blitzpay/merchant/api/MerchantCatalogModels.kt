package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class CatalogProductPage(
    val items: List<ProductResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val limit: Int,
)

data class BranchCatalogCategory(
    val id: UUID,
    val name: String,
    val productCount: Long,
    val estimatedDurationMinutes: Int?,
    val language: String?,
    val imageUrl: String? = null,
)

data class BranchCatalogCategories(
    val categories: List<BranchCatalogCategory>,
    val uncategorizedCount: Long,
    val totalCount: Long,
)

data class CatalogThumbnailBatch(
    val updated: Int,
    val skipped: Int,
    val failed: Int,
    val nextCursor: String?,
    val hasMore: Boolean,
)
