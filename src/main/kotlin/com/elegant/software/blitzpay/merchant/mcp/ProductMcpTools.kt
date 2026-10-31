package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BulkFailedItem
import com.elegant.software.blitzpay.merchant.api.BulkProductInput
import com.elegant.software.blitzpay.merchant.api.BulkProductUpsertResult
import com.elegant.software.blitzpay.merchant.api.BulkSkippedItem
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.service.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.service.MerchantProductService
import com.elegant.software.blitzpay.merchant.service.ProductImageCrop
import com.elegant.software.blitzpay.merchant.service.ProductImageDownloader
import com.elegant.software.blitzpay.merchant.service.ProductImagePolicy
import com.elegant.software.blitzpay.merchant.service.ProductImageProcessor
import com.elegant.software.blitzpay.merchant.service.ProductImageUpload
import com.elegant.software.blitzpay.merchant.service.RawProductImage
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Base64
import java.util.LinkedHashSet
import java.util.UUID

@Suppress("unused")
@Component
class ProductMcpTools(
    private val merchantProductService: MerchantProductService,
    private val merchantProductCategoryService: MerchantProductCategoryService,
    private val productImageDownloader: ProductImageDownloader,
    private val productImageProcessor: ProductImageProcessor,
) {

    @McpTool(
        name = "merchant_product_update",
        description = "Update a product's name, description, price, and category for a merchant. " +
            "Supply categoryName to have the server resolve or create the category automatically, or supply categoryId directly. " +
            "Optional product image — see the image parameter descriptions or merchant_api_guide (PRODUCT IMAGES) for choosing imageUrl vs imageBase64.",
        annotations = McpTool.McpAnnotations(title = "Product Update")
    )
    fun updateProduct(
        merchantId: String,
        productId: String,
        branchId: String,
        name: String,
        unitPrice: String,
        description: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        productCode: String? = null,
        @McpToolParam(required = false, description = ImageBase64Description)
        imageBase64: String? = null,
        @McpToolParam(required = false, description = ImageContentTypeDescription)
        imageContentType: String? = null,
        @McpToolParam(required = false, description = ImageUrlDescription)
        imageUrl: String? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropX: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropY: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropWidth: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropHeight: Int? = null
    ): ProductResponse {
        val mId = UUID.fromString(merchantId)
        val pId = UUID.fromString(productId)
        return merchantProductService.updateIncludingInactive(
            merchantId = mId,
            productId = pId,
            request = UpdateProductRequest(
                name = name,
                branchId = UUID.fromString(branchId),
                unitPrice = BigDecimal(unitPrice),
                description = description,
                categoryId = resolveOrCreateCategory(mId, categoryId, categoryName),
                productCode = productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
            ),
            image = productImageUploadOrNull(
                imageBase64 = imageBase64,
                imageContentType = imageContentType,
                imageUrl = imageUrl,
                cropX = cropX,
                cropY = cropY,
                cropWidth = cropWidth,
                cropHeight = cropHeight
            )
        )
    }

    @McpTool(
        name = "product_id_by_name",
        description = "Get product ID by product name, merchant ID, and branch ID",
        annotations = McpTool.McpAnnotations(title = "Product ID by Name")
    )
    fun getProductIdByName(merchantId: String, branchId: String, productName: String): String {
        return merchantProductService.findByNameIncludingInactive(
            UUID.fromString(merchantId),
            UUID.fromString(branchId),
            productName
        )?.productId?.toString() ?: throw IllegalArgumentException("Product not found with name: $productName")
    }

    @McpTool(
        name = "product_id_by_name_or_create",
        description = "Get or create a SINGLE product by name. " +
            "Supply categoryName to have the server resolve or create the category automatically, or supply categoryId directly. " +
            "Optional product image — see the image parameter descriptions or merchant_api_guide (PRODUCT IMAGES) for choosing imageUrl vs imageBase64. " +
            "WARNING: if you need to create or update 2 or more products, call products_bulk_upsert instead — do NOT call this tool in a loop.",
        annotations = McpTool.McpAnnotations(title = "Product: Get or Create")
    )
    fun getOrCreateProductId(
        merchantId: String,
        branchId: String,
        productName: String,
        unitPrice: String,
        description: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        productCode: String? = null,
        @McpToolParam(required = false, description = ImageBase64Description)
        imageBase64: String? = null,
        @McpToolParam(required = false, description = ImageContentTypeDescription)
        imageContentType: String? = null,
        @McpToolParam(required = false, description = ImageUrlDescription)
        imageUrl: String? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropX: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropY: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropWidth: Int? = null,
        @McpToolParam(required = false, description = CropDescription)
        cropHeight: Int? = null
    ): String {
        val mId = UUID.fromString(merchantId)
        val bId = UUID.fromString(branchId)
        val resolvedCategoryId = resolveOrCreateCategory(mId, categoryId, categoryName)
        val parsedCode = productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
        val image = productImageUploadOrNull(
            imageBase64 = imageBase64,
            imageContentType = imageContentType,
            imageUrl = imageUrl,
            cropX = cropX,
            cropY = cropY,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
        val existing = merchantProductService.findByNameIncludingInactive(mId, bId, productName)
        if (existing != null) {
            if (image != null) {
                merchantProductService.update(
                    mId,
                    existing.productId,
                    UpdateProductRequest(
                        name = productName,
                        branchId = bId,
                        unitPrice = BigDecimal(unitPrice),
                        description = description ?: existing.description,
                        categoryId = resolvedCategoryId,
                        productCode = parsedCode
                    ),
                    image
                )
            }
            return existing.productId.toString()
        }

        return merchantProductService.create(
            mId,
            CreateProductRequest(
                name = productName,
                branchId = bId,
                unitPrice = BigDecimal(unitPrice),
                description = description,
                categoryId = resolvedCategoryId,
                productCode = parsedCode
            ),
            image,
            active = false
        ).productId.toString()
    }

    @McpTool(
        name = "products_bulk_upsert",
        description = "PREFERRED tool when working with 2 or more products — use this instead of calling product_id_by_name_or_create in a loop. " +
            "Creates new products and updates existing ones in a single call. " +
            "Lookup key: productCode when provided, otherwise product name. " +
            "Returns created, updated, skipped (within-batch duplicates only), and failed items with their IDs. " +
            "Accepts categoryName per item (resolved or created automatically) or categoryId. " +
            "Re-running the same payload is safe — existing products are updated, not duplicated. " +
            "Max 200 items per call. Images are not supported in bulk; use merchant_product_update after import (see merchant_api_guide, PRODUCT IMAGES).",
        annotations = McpTool.McpAnnotations(title = "Products: Bulk Upsert")
    )
    fun bulkUpsertProducts(
        merchantId: String,
        branchId: String,
        products: List<BulkProductInput>
    ): BulkProductUpsertResult {
        require(products.size <= 200) { "Batch size must not exceed 200 items" }
        val mId = UUID.fromString(merchantId)
        val bId = UUID.fromString(branchId)
        val created = mutableListOf<ProductResponse>()
        val updated = mutableListOf<ProductResponse>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()
        val seenNames = LinkedHashSet<String>()
        val seenCodes = LinkedHashSet<Long>()

        for (input in products) {
            val normalizedName = input.productName.trim()
            val parsedCode = input.productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()

            if (!seenNames.add(normalizedName.lowercase())) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate within batch"))
                continue
            }
            if (parsedCode != null && !seenCodes.add(parsedCode)) {
                skipped.add(BulkSkippedItem(name = normalizedName, reason = "duplicate productCode within batch"))
                continue
            }

            val resolvedCategoryId: UUID? = try {
                resolveOrCreateCategory(mId, input.categoryId, input.categoryName)
            } catch (ex: Exception) {
                failed.add(BulkFailedItem(name = normalizedName, reason = "Category error: ${ex.message ?: "Unknown error"}"))
                continue
            }

            val existing = parsedCode?.let { merchantProductService.findByProductCode(mId, bId, it) }
                ?: merchantProductService.findByNameIncludingInactive(mId, bId, normalizedName)

            if (existing != null) {
                runCatching {
                    merchantProductService.updateIncludingInactive(
                        mId,
                        existing.productId,
                        UpdateProductRequest(
                            name = normalizedName,
                            branchId = bId,
                            unitPrice = BigDecimal(input.unitPrice),
                            description = input.description,
                            categoryId = resolvedCategoryId,
                            productCode = parsedCode
                        )
                    )
                }.onSuccess { response ->
                    updated.add(response)
                }.onFailure { ex ->
                    failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
                }
            } else {
                runCatching {
                    merchantProductService.create(
                        mId,
                        CreateProductRequest(
                            name = normalizedName,
                            branchId = bId,
                            unitPrice = BigDecimal(input.unitPrice),
                            description = input.description,
                            categoryId = resolvedCategoryId,
                            productCode = parsedCode
                        ),
                        null,
                        active = false
                    )
                }.onSuccess { response ->
                    created.add(response)
                }.onFailure { ex ->
                    failed.add(BulkFailedItem(name = normalizedName, reason = ex.message ?: "Unknown error"))
                }
            }
        }
        return BulkProductUpsertResult(created = created, updated = updated, skipped = skipped, failed = failed)
    }

    private fun resolveOrCreateCategory(merchantId: UUID, categoryId: String?, categoryName: String?): UUID? {
        val trimmedId = categoryId?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedName = categoryName?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            trimmedId != null -> UUID.fromString(trimmedId)
            trimmedName != null -> merchantProductCategoryService.findByName(merchantId, trimmedName)?.id
                ?: merchantProductCategoryService.create(merchantId, CreateProductCategoryRequest(name = trimmedName)).id
            else -> null
        }
    }

    private fun productImageUploadOrNull(
        imageBase64: String?,
        imageContentType: String?,
        imageUrl: String?,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?
    ): ProductImageUpload? {
        val normalizedBase64 = imageBase64?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedContentType = imageContentType?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
        val cropValues = listOf(cropX, cropY, cropWidth, cropHeight)

        require(normalizedBase64 == null || normalizedUrl == null) {
            "Provide either imageBase64 or imageUrl, not both"
        }
        require(cropValues.all { it == null } || cropValues.all { it != null }) {
            "cropX, cropY, cropWidth, and cropHeight must all be provided to crop an image"
        }

        val source = when {
            normalizedUrl != null -> productImageDownloader.download(normalizedUrl)
            normalizedBase64 != null -> {
                val contentType = normalizedContentType
                    ?: throw IllegalArgumentException("imageContentType is required when imageBase64 is provided")
                ProductImagePolicy.extensionFor(contentType)
                RawProductImage(
                    contentType = contentType,
                    bytes = Base64.getDecoder().decode(normalizedBase64.substringAfter("base64,", normalizedBase64))
                )
            }
            else -> {
                require(normalizedContentType == null && cropValues.all { it == null }) {
                    "imageBase64 or imageUrl is required when imageContentType or crop parameters are provided"
                }
                return null
            }
        }

        val crop = if (cropX != null) ProductImageCrop(cropX, cropY!!, cropWidth!!, cropHeight!!) else null
        return productImageProcessor.prepare(source, crop)
    }

    private companion object {
        const val ImageUrlDescription =
            "PREFERRED image source. Public http(s) URL of a JPEG, PNG, or WebP product image, up to 15 MB; " +
                "the server downloads it (no auth, cookies, or private/internal hosts) and resizes images over 5 MB. " +
                "Use whenever the image already exists online (merchant website, CDN, marketplace listing). Do not combine with imageBase64. " +
                "Images under 400 px on the shorter side, blurry, too dark, or blank are rejected with the reason."
        const val ImageBase64Description =
            "Fallback image source, ONLY for a small image (encoded string under ~200 KB, i.e. original under ~150 KB) " +
                "that has no public URL, e.g. a local file or an image pasted into the chat. Requires imageContentType. " +
                "For a larger image with no URL, do not send it here; ask the user for a public link instead. Do not combine with imageUrl."
        const val ImageContentTypeDescription =
            "Required with imageBase64 only: image/jpeg, image/png, or image/webp. Ignored for imageUrl (type is detected from the file)."
        const val CropDescription =
            "Optional crop in source-image pixels; give all of cropX, cropY, cropWidth, cropHeight or none. JPEG/PNG only."
    }
}
