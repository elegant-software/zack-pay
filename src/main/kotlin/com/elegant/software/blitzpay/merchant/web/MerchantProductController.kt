package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.service.MenuLanguageService
import com.elegant.software.blitzpay.merchant.service.MerchantProductService
import com.elegant.software.blitzpay.merchant.service.ProductImageUpload
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.util.UUID

data class ProductErrorResponse(val error: String)

@Tag(name = "Merchant Products", description = "Product catalog management for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/products", version = "1")
class MerchantProductController(
    private val merchantProductService: MerchantProductService,
    private val menuLanguageService: MenuLanguageService,
) {

    @Operation(summary = "Create a product for the merchant")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @PathVariable merchantId: UUID,
        @RequestPart("name") name: String,
        @RequestPart("branchId") branchId: String,
        @RequestPart("unitPrice") unitPrice: String,
        @RequestPart("description", required = false) description: String?,
        @RequestPart("categoryId", required = false) categoryId: String?,
        @RequestPart("productCode", required = false) productCode: String?,
        @RequestPart("image", required = false) image: MultipartFile?,
    ): ResponseEntity<ProductResponse> {
        val response = merchantProductService.create(
            merchantId,
            CreateProductRequest(
                name = name,
                branchId = UUID.fromString(branchId),
                unitPrice = BigDecimal(unitPrice),
                description = description,
                categoryId = categoryId.toUuidOrNull(),
                productCode = productCode.toLongOrNull(),
            ),
            image?.toProductImageUpload()
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "List products for the merchant",
        description = "Name and description follow Accept-Language when the merchant offers International Menu, " +
            "or the explicit language parameter; untranslated items fall back to the Original Menu Language. " +
            "The language field states which language was returned."
    )
    @GetMapping
    fun list(
        @PathVariable merchantId: UUID,
        @RequestParam("branchId") branchId: UUID,
        @RequestParam("categoryId", required = false) categoryId: UUID?,
        @RequestParam("language", required = false) language: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<List<ProductResponse>> =
        ResponseEntity.ok(
            merchantProductService.list(
                merchantId, branchId, categoryId,
                menuLanguageService.resolve(merchantId, language, acceptLanguage)
            )
        )

    @Operation(summary = "Get a single product")
    @GetMapping("/{productId}")
    fun get(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestParam("branchId", required = false) branchId: UUID?,
        @RequestParam("language", required = false) language: String?,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.ok(
            merchantProductService.get(
                merchantId, productId, branchId,
                menuLanguageService.resolve(merchantId, language, acceptLanguage)
            )
        )

    @Operation(summary = "Update a product's name, price, and image")
    @PutMapping("/{productId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun update(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestPart("name") name: String,
        @RequestPart("branchId") branchId: String,
        @RequestPart("unitPrice") unitPrice: String,
        @RequestPart("description", required = false) description: String?,
        @RequestPart("categoryId", required = false) categoryId: String?,
        @RequestPart("productCode", required = false) productCode: String?,
        @RequestPart("image", required = false) image: MultipartFile?,
    ): ResponseEntity<ProductResponse> =
        ResponseEntity.ok(
            merchantProductService.update(
                merchantId, productId,
                UpdateProductRequest(
                    name = name,
                    branchId = UUID.fromString(branchId),
                    unitPrice = BigDecimal(unitPrice),
                    description = description,
                    categoryId = categoryId.toUuidOrNull(),
                    productCode = productCode.toLongOrNull(),
                ),
                image?.toProductImageUpload()
            )
        )

    @Operation(summary = "Soft-delete a product (sets active = false)")
    @DeleteMapping("/{productId}")
    fun deactivate(
        @PathVariable merchantId: UUID,
        @PathVariable productId: UUID,
        @RequestParam("branchId") branchId: UUID,
    ): ResponseEntity<Void> {
        merchantProductService.deactivate(merchantId, productId, branchId)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.badRequest().body(ProductErrorResponse(ex.message ?: "Invalid product request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProductErrorResponse(ex.message ?: "Product not found"))

    @ExceptionHandler(IllegalStateException::class)
    fun storageUnavailable(ex: IllegalStateException): ResponseEntity<ProductErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProductErrorResponse(ex.message ?: "Product storage unavailable"))

    private fun MultipartFile.toProductImageUpload(): ProductImageUpload {
        val ct = contentType ?: throw IllegalArgumentException("Product image content type is required")
        return ProductImageUpload(contentType = ct, bytes = bytes)
    }

    private fun String?.toUuidOrNull(): UUID? = this?.trim()?.takeIf { it.isNotEmpty() }?.let(UUID::fromString)
    private fun String?.toLongOrNull(): Long? = this?.trim()?.takeIf { it.isNotEmpty() }?.toLong()
}
