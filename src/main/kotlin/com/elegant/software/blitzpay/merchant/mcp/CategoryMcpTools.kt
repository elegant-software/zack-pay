package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BulkCategoryCreateResult
import com.elegant.software.blitzpay.merchant.api.BulkCategoryInput
import com.elegant.software.blitzpay.merchant.api.BulkFailedItem
import com.elegant.software.blitzpay.merchant.api.BulkSkippedItem
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.util.LinkedHashSet
import java.util.UUID

@Suppress("unused")
@Component
class CategoryMcpTools(
    private val merchantProductCategoryService: MerchantProductCategoryService,
) {

    @McpTool(
        name = "category_id_by_name",
        description = "Get product category ID by name for a given merchant",
        annotations = McpTool.McpAnnotations(title = "Category ID by Name")
    )
    fun getCategoryIdByName(merchantId: String, categoryName: String): String {
        return merchantProductCategoryService.findByName(UUID.fromString(merchantId), categoryName)?.id?.toString()
            ?: throw IllegalArgumentException("Category not found: $categoryName")
    }

    @McpTool(
        name = "category_id_by_name_or_create",
        description = "Get or create a SINGLE product category by name. " +
            "WARNING: if you need to create 2 or more categories, call categories_bulk_create instead — do NOT call this tool in a loop.",
        annotations = McpTool.McpAnnotations(title = "Category: Get or Create")
    )
    fun getOrCreateCategoryId(merchantId: String, categoryName: String): String {
        val mId = UUID.fromString(merchantId)
        return merchantProductCategoryService.findByName(mId, categoryName)?.id?.toString()
            ?: merchantProductCategoryService.create(
                mId,
                CreateProductCategoryRequest(name = categoryName)
            ).id.toString()
    }

    @McpTool(
        name = "merchant_list_product_categories",
        description = "List all product categories for a merchant",
        annotations = McpTool.McpAnnotations(title = "List Product Categories")
    )
    fun listProductCategories(merchantId: String): List<ProductCategoryResponse> =
        merchantProductCategoryService.list(UUID.fromString(merchantId))

    @McpTool(
        name = "categories_bulk_create",
        description = "PREFERRED tool when creating 2 or more categories — use this instead of calling category_id_by_name_or_create in a loop. " +
            "Creates multiple product categories for a merchant in a single call. " +
            "Returns created, skipped (already-existing or within-batch duplicates), and failed items with their IDs. " +
            "Max 200 items per call.",
        annotations = McpTool.McpAnnotations(title = "Categories: Bulk Create")
    )
    fun bulkCreateCategories(
        merchantId: String,
        categories: List<BulkCategoryInput>
    ): BulkCategoryCreateResult {
        require(categories.size <= 200) { "Batch size must not exceed 200 items" }
        val mId = UUID.fromString(merchantId)
        val created = mutableListOf<ProductCategoryResponse>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()
        val seenNames = LinkedHashSet<String>()

        for (input in categories) {
            val normalizedName = input.categoryName.trim()
            if (!seenNames.add(normalizedName.lowercase())) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate within batch"))
                continue
            }
            val existing = merchantProductCategoryService.findByName(mId, normalizedName)
            if (existing != null) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "already exists", existingId = existing.id.toString()))
                continue
            }
            runCatching {
                merchantProductCategoryService.create(mId, CreateProductCategoryRequest(name = normalizedName))
            }.onSuccess { response ->
                created.add(response)
            }.onFailure { ex ->
                failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
            }
        }
        return BulkCategoryCreateResult(created = created, skipped = skipped, failed = failed)
    }
}
