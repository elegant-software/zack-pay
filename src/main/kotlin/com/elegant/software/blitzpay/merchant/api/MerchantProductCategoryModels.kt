package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

data class CreateProductCategoryRequest(
    val name: String,
    val estimatedDurationMinutes: Int? = null,
)

data class UpdateProductCategoryRequest(
    val name: String,
    val estimatedDurationMinutes: Int? = null,
)

data class ProductCategoryResponse(
    val id: UUID,
    val name: String,
    val estimatedDurationMinutes: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BulkCategoryInput(
    val categoryName: String,
    val estimatedDurationMinutes: Int? = null,
)

data class BulkCategoryCreateResult(
    val created: List<ProductCategoryResponse>,
    val skipped: List<BulkSkippedItem>,
    val failed: List<BulkFailedItem>,
)
