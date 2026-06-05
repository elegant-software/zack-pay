package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BulkFailedItem
import com.elegant.software.blitzpay.merchant.api.BulkProductInput
import com.elegant.software.blitzpay.merchant.api.BulkProductUpsertResult
import com.elegant.software.blitzpay.merchant.api.BulkSkippedItem
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.ProductImagePolicy
import com.elegant.software.blitzpay.merchant.application.ProductImageUpload
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.Base64
import java.util.LinkedHashSet
import java.util.UUID
import javax.imageio.ImageIO

@Suppress("unused")
@Component
class ProductMcpTools(
    private val merchantProductService: MerchantProductService,
    private val merchantProductCategoryService: MerchantProductCategoryService,
) {

    @McpTool(
        name = "merchant_product_update",
        description = "Update a product's name, description, price, and category for a merchant. " +
            "Supply categoryName to have the server resolve or create the category automatically, or supply categoryId directly. " +
            "Optional product image upload uses imageBase64 and imageContentType only. " +
            "Images must be clear, high-quality, directly related to the product, JPEG/PNG/WebP, and 5 MB or smaller."
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
        imageBase64: String? = null,
        imageContentType: String? = null,
        cropX: Int? = null,
        cropY: Int? = null,
        cropWidth: Int? = null,
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
                cropX = cropX,
                cropY = cropY,
                cropWidth = cropWidth,
                cropHeight = cropHeight
            )
        )
    }

    @McpTool(
        name = "product_id_by_name",
        description = "Get product ID by product name, merchant ID, and branch ID"
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
            "Optional product image upload uses imageBase64 and imageContentType only. " +
            "Images must be clear, high-quality, directly related to the product, JPEG/PNG/WebP, and 5 MB or smaller. " +
            "WARNING: if you need to create or update 2 or more products, call products_bulk_upsert instead — do NOT call this tool in a loop."
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
        imageBase64: String? = null,
        imageContentType: String? = null,
        cropX: Int? = null,
        cropY: Int? = null,
        cropWidth: Int? = null,
        cropHeight: Int? = null
    ): String {
        val mId = UUID.fromString(merchantId)
        val bId = UUID.fromString(branchId)
        val resolvedCategoryId = resolveOrCreateCategory(mId, categoryId, categoryName)
        val parsedCode = productCode?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
        val image = productImageUploadOrNull(
            imageBase64 = imageBase64,
            imageContentType = imageContentType,
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
            "Max 200 items per call. Images are not supported in bulk; use merchant_product_update after import with clear, high-quality, directly related JPEG/PNG/WebP images that are 5 MB or smaller."
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
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?
    ): ProductImageUpload? {
        val normalizedBase64 = imageBase64?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedContentType = imageContentType?.trim()?.takeIf { it.isNotEmpty() }

        if (normalizedBase64 == null) {
            require(listOf(normalizedContentType, cropX, cropY, cropWidth, cropHeight).all { it == null }) {
                "imageBase64 is required when imageContentType or crop parameters are provided"
            }
            return null
        }

        val contentType = normalizedContentType
            ?: throw IllegalArgumentException("imageContentType is required when imageBase64 is provided")
        ProductImagePolicy.extensionFor(contentType)
        val originalBytes = Base64.getDecoder().decode(normalizedBase64.substringAfter("base64,", normalizedBase64))
        val imageBytes = cropImageIfRequested(
            bytes = originalBytes,
            contentType = contentType,
            cropX = cropX,
            cropY = cropY,
            cropWidth = cropWidth,
            cropHeight = cropHeight
        )
        return ProductImageUpload(contentType = contentType, bytes = imageBytes)
    }

    private fun cropImageIfRequested(
        bytes: ByteArray,
        contentType: String,
        cropX: Int?,
        cropY: Int?,
        cropWidth: Int?,
        cropHeight: Int?
    ): ByteArray {
        val cropValues = listOf(cropX, cropY, cropWidth, cropHeight)
        if (cropValues.all { it == null }) return bytes
        require(cropValues.all { it != null }) {
            "cropX, cropY, cropWidth, and cropHeight must all be provided to crop an image"
        }
        require(cropWidth!! > 0 && cropHeight!! > 0) { "cropWidth and cropHeight must be positive" }
        require(contentType.lowercase() != "image/webp") { "Server-side crop is supported for JPEG and PNG images only" }

        val source = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Unable to decode product image")
        require(cropX!! >= 0 && cropY!! >= 0) { "cropX and cropY must be >= 0" }
        require(cropX + cropWidth <= source.width && cropY + cropHeight <= source.height) {
            "Crop rectangle exceeds image bounds ${source.width}x${source.height}"
        }

        val cropped = source.getSubimage(cropX, cropY, cropWidth, cropHeight)
        val normalized = normalizeForContentType(cropped, contentType)
        val output = ByteArrayOutputStream()
        val format = ProductImagePolicy.extensionFor(contentType).let { if (it == "jpg") "jpeg" else it }
        require(ImageIO.write(normalized, format, output)) {
            "Unable to encode cropped product image as $contentType"
        }
        return output.toByteArray()
    }

    private fun normalizeForContentType(image: BufferedImage, contentType: String): BufferedImage {
        if (contentType.lowercase() != "image/jpeg") return image
        val normalized = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = normalized.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return normalized
    }
}
